package edu.asu.cse512;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.broadcast.Broadcast;

import scala.Tuple2;

public class SpatialJoin implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static void spatialJoinQuery(String inputLocation1, String inputLocation2, String outputLocation, final String input1Type) throws FileNotFoundException
	{
		try {
			JavaSparkContext sc = Join.sparkContext;
	        URI uri = URI.create(inputLocation1);
	        final Path path = new Path(uri);
	        final FileSystem fileSys = FileSystem.get(uri, new Configuration());
	        final List<Double> input1Points=new ArrayList<Double>();
	        BufferedReader br = new BufferedReader(new InputStreamReader(fileSys.open(path)));
	        String line;
	        while ((line = br.readLine()) != null) {
	            String[] separateInput1=line.split(",");
	            input1Points.add(Double.parseDouble(separateInput1[0]));
	            input1Points.add(Double.parseDouble(separateInput1[1]));
	            input1Points.add(Double.parseDouble(separateInput1[2]));
	            if(input1Type.equalsIgnoreCase("rectangle")){
	                input1Points.add(Double.parseDouble(separateInput1[3]));
	                input1Points.add(Double.parseDouble(separateInput1[4]));

	            }
	        }
	        final Broadcast<List<Double>> broadcastedInput1=sc.broadcast(input1Points);
	        JavaRDD<String> input2 = sc.textFile(inputLocation2);
	        JavaPairRDD<ArrayList<String>, String> processRDD = input2
	                .mapToPair(new PairFunction<String, ArrayList<String>, String>() {
	                    private static final long serialVersionUID = 1L;

	                    public Tuple2<ArrayList<String>, String> call(String arg0) throws Exception {
	                        ArrayList<String> output = new ArrayList<String>();
	                        ArrayList<String> finalOutput = new ArrayList<String>();
	                        String[] separateInput2 = arg0.split(",");
	                        String bid = separateInput2[0];
	                        double x1 = Double.parseDouble(separateInput2[1]);
	                        double y1 = Double.parseDouble(separateInput2[2]);
	                        double x2 = Double.parseDouble(separateInput2[3]);
	                        double y2 = Double.parseDouble(separateInput2[4]);

	                        ArrayList<String> aids = new ArrayList<String>();
	                        List<Double> finalInput1=broadcastedInput1.value();
	                        for (int i=0;i<finalInput1.size();i++){
	                            String tempAid=String.valueOf(finalInput1.get(i).intValue());
	                            double a1 = finalInput1.get(++i);
	                            double b1 = finalInput1.get(++i);
	                            if(input1Type.equalsIgnoreCase("rectangle")){
	                                double a2 = finalInput1.get(++i);
	                                double b2 = finalInput1.get(++i);


	                                if(((Math.min(x1, x2) <= a1 && Math.max(x1, x2) >= a1) || (Math.min(x1, x2) <= a2 && Math.max(x1, x2) >= a2))&& ((Math.min(y1, y2) <= b1 && Math.max(y1, y2) >= b1) || (Math.min(y1, y2) <= b2 && Math.max(y1, y2) >= b2)))
	                                {
	                                    String aid = tempAid;
	                                    aids.add(aid);
	                                }
	                                else if((Math.min(x1, x2) <= a1 && Math.max(x1, x2) >= a1) || (Math.min(x1, x2) <= a2 && Math.max(x1, x2) >= a2)){
	                                    if((Math.max(b1, b2) - Math.min(b1, b2)) > ((Math.max(y1, y2) - (Math.min(y1, y2))))){
	                                        String aid = tempAid;
	                                        aids.add(aid);
	                                    }
	                                }
	                                else if((Math.min(y1, y2) <= b1 && Math.max(y1, y2) >= b1) || (Math.min(y1, y2) <= b2 && Math.max(y1, y2) >= b2)){
	                                    if((Math.max(a1, a2) - Math.min(a1, a2)) > ((Math.max(x1, x2) - (Math.min(x1, x2))))){
	                                        String aid = tempAid;
	                                        aids.add(aid);
	                                    }
	                                }
	                                else if((Math.max(a1, a2) >= Math.max(x1, x2)) && (Math.min(a1, a2) <= Math.min(x1, x2)) && (Math.max(b1, b2) >= Math.max(y1, y2)) && (Math.min(b1, b2) <= Math.min(y1, y2))){
	                                    String aid = tempAid;
	                                    aids.add(aid);
	                                }
	                            }
	                            else{
	                                if(a1 <= Math.max(x1, x2) && b1<=Math.max(y1, y2) && a1>=Math.min(x1, x2) && b1 >= Math.min(y1, y2)){
	                                    String aid = tempAid;
	                                    aids.add(aid);
	                                }
	                            }
	                        }


	                        if(!CollectionUtils.isEmpty(aids))
	                        {
	                            aids.add(0, bid);
	                            output.addAll(aids);
	                        }

	                        finalOutput.addAll(output);
	                        if(CollectionUtils.isEmpty(finalOutput))
	                        {


	                            for (int i=0;i<finalInput1.size();i++){
	                                String tempAid=String.valueOf(finalInput1.get(i).intValue());
	                                if(input1Type.equalsIgnoreCase("rectangle")){
	                                    i=i+4;

	                                }
	                                else i=i+2;

	                                finalOutput.add(tempAid+",NULL");
	                            }
	                        }

	                        return new Tuple2<ArrayList<String>, String>(finalOutput, "");
	                    }
	                });
	        JavaRDD<String> outputData = processRDD.map(new Function<Tuple2<ArrayList<String>,String>, String>() {

	            public String call(Tuple2<ArrayList<String>, String> data) throws Exception {
	                String result = data._1().get(0);
	                for(int i =1;i<data._1().size();i++){
	                    result += ",";
	                    result+=data._1().get(i);
	                }
	                return result;
	            }
	        }).repartition(1).sortBy(new Function<String, String>() {

	            public String call(String str) throws Exception {
	                return str;
	            }
	        }, true, 1);

			int index=outputLocation.lastIndexOf("/");
			String hadoopPath=outputLocation.substring(0, index);
			String intermediateResultpath=hadoopPath+"/IntermediateSpatialJoinResult";

			FileToCsv.deleteExistingFile(intermediateResultpath);
			outputData.saveAsTextFile(intermediateResultpath);

			FileToCsv.fileToCsv(intermediateResultpath, outputLocation);

	}
	catch(Exception e)
	{
		e.printStackTrace();
	}
}
}