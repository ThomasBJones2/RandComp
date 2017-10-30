package com.criticalityworkbench.randcomphandler;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import java.util.*;
import java.io.*;

//This class is used to build graphs from raw experiment data files

public class NumBuilder extends DataExtractor {


	String imageRootDirectory = "./output_images/";
	String rawDataOutputDirectory = "./output/";
	String processedRootDirectory = "./output_processed/";
	String processedDataDirectory = "./FinalNums/";

	public static void main(String[] args){
		if(args[0].equals("h") || args[0].equals("H")){
			System.out.println("Welcome to the NumberBuilding Service");
			System.out.println("There are two ways to use this service: \n" +
					"(1) java.jar <name> Input_Object Main_Object Experiment_Type  OR \n"+
				  "(2) java.jar <name> Input_Object Main_Object Experiment_Type "
 					+ "Data_output_directory");
		} else {


			NumBuilder theBuilder = new NumBuilder(args[0], args[1], args[2]);

			if(args.length == 4){
				theBuilder.processedDataDirectory = args[3];
			}

			for(int inputSize : Experimenter.inputSizes){
				theBuilder.readDataIn(inputSize);
				//Here is where we actually print stuff out!
				theBuilder.printAllProcessedData(
					new DataEnsemble<EnsTriple>(readInLocations,EnsTriple::new ), inputSize);
			}

		}
	}

	NumBuilder(String inputClassName, String experimentClassName, String experimentTypeName){
			super(inputClassName, experimentClassName, experimentTypeName);
	}


	private static String strip(String in){
		String[] outStrings = in.split("[.]");
		if(outStrings.length > 0)
			return outStrings[outStrings.length - 1];
		else
			return "";
	}

	private static String createDirectory(String directory,
			String inputClassName, 
			String experimentClassName, 
			String experimentTypeName){
			return directory + 
						strip(inputClassName) + "-" +
						strip(experimentClassName) + "-" +
						strip(experimentTypeName) + "/";
	}

	private static String createFile(String directory,
			String inputClassName, 
			String experimentClassName, 
			String experimentTypeName,
			String locationName,
			String scoreName,
			int inputSize){
			return createDirectory(directory, 
						inputClassName,
						experimentClassName, 
						experimentTypeName) +
						locationName + "-" +
						scoreName + "-" +
						inputSize + ".csv";
	}




	private void printAllProcessedData(DataEnsemble<EnsTriple> dataEnsemble, 
			int inputSize
			){

		System.out.println(dataEnsemble.scores.size());

		//String[][] theData = new double[dataEnsembe.scores.maxLocationSize()][1 + dataEnsemble.scores.size()*2 + 2 + 2];
		//clearOutputOnInputSize(processedRootDirectory, inputSize, ".csv");
		for(int i = 0; i < dataEnsemble.scores.size(); i ++) {
			DataEnsemble<EnsTriple>.EnsScore score = dataEnsemble.scores.get(i);
			for(int j = 0; j < score.locations.size(); j ++){
				DataEnsemble<EnsTriple>.EnsLocation location = score.locations.get(j);

				String[][] theData = new String[location.triples.size() + 1][8];
				String[] headers = {"#location value,", 
					"score average at location,", 
					"score std error at location,",
					"number of data points,",
					"score median at location,",
					"median of all scores,",
					"average timeCount (number of fallible operations executed),",
					"average failCount (number of fallible operations that actually failed),"
				};

				theData[0] = headers;
				
				for(int q = 0; q < location.triples.size(); q ++) {
					EnsTriple triple = location.triples.get(q);
					theData[q+1][0] = "" + triple.location;
					theData[q+1][1] = "" + triple.avg;
					theData[q+1][2] = "" + triple.stdErr;
					theData[q+1][3] = "" + triple.count;
					theData[q+1][4] = "" + triple.median;
					theData[q+1][5] = "" + location.median;
					theData[q+1][6] = "" + triple.avgTimeCount;
					theData[q+1][7] = "" + triple.avgFailCount;
				}
		

				String directoryName = createDirectory(processedDataDirectory, 
					inputClassName, 
					experimentClassName, 
					experimentTypeName);

				String fileName = createFile(processedDataDirectory, 
					inputClassName, 
					experimentClassName, 
					experimentTypeName,
					location.name, 
					score.name,
					inputSize);
			
				try{

    			File directory = new File(String.valueOf(directoryName));
    			if (! directory.exists()){
        		directory.mkdir();
        		// If you require it to make the entire directory path including parents,
        		// use directory.mkdirs(); here instead.
    			}

					CSVWriter outputWriter = new CSVWriter(new FileWriter(new File(fileName)), 
						' ',
						CSVWriter.NO_QUOTE_CHARACTER);
					for(String[] line : theData){
						outputWriter.writeNext(line);
					}
					outputWriter.close();
				}	catch (IOException e){

					System.out.println("Couldn't write output to file " + e);
				}	
			}
		}

	}
}