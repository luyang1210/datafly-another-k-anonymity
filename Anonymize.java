//import statements
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Copy;
import weka.filters.unsupervised.attribute.Discretize;
import weka.filters.unsupervised.attribute.Reorder;
import weka.filters.unsupervised.instance.RemoveWithValues;
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Anonymize takes an arff file and performs the datafly algorithm to achieve 
 *  k-anonymity 
 * 
 * 
 * Datafly Algorithm: mention by L. Sweeney in his paper 
 *  "Achieving k-anonymity privacy protection using generalization and sup-
 *   pression"
 * 
 * @author Eitan Romanoff ear7631@cs.rit.edu
 * @author  Nidhin Pattaniyil ntp5633@cs.rit.edu
 */
public class Anonymize {
    
    //location to save the preprocessed file
    private File outFile;
    //location of the file to preproces
    private File inFile;
    //list of attribute names in the arff file
    private LinkedList<String> attributeNames;
    //list of attributes that are considered quasi ids
    private LinkedList<String> secureAttributes;
    //list of attributes
    private FastVector wekaAttributes;
    //the name of the dataset in the arff file
    private String relationName;
    //set of all the records in the arff file
    private Instances instances;
    //the k in the k-anonymity
    private int k_anonymity_constant;
    //a predefined value of k in k-anonymity
    private static final int K = 50;
    
    /**
     * Constructor 
     *  Uses a predefined value of k to achieve k-anonymity
     * @param inFile source of the instances to anonymize
     * @param outFile destination of the preprocessed instances
     */
    public Anonymize(File inFile, File outFile) {
        this(inFile, outFile, K);
    }
    
    /**
     * Constructor 
     *  Initialize the location of the source arrf fill and the path
     *  to save the preprocessed arff file. Accepts a user defined k for
     *  k-anonymity
     * 
     * @param inFile source of the instances to anonymize
     * @param outFile destination of the preprocessed instances
     * @param k 
     */
    public Anonymize(File inFile, File outFile, int k) {
        this.k_anonymity_constant = k;
        this.inFile = inFile;
        this.outFile = outFile;
        this.secureAttributes = new LinkedList<String>();
        this.attributeNames = new LinkedList<String>();
        wekaAttributes = new FastVector();
    }
    
    /**
     * Reads the information from the arrf file such as 
     * the meta data and instances
     */
    public void readFile() {
        System.out.print("Loading file... ");
        Scanner scanner = null;
        try {
            scanner = new Scanner(inFile);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Anonymize.class.getName()).log(Level.SEVERE, 
                    "Failed in reading the data to anonymuze.", ex);
        }
        
        
        boolean done = false;
        String[] tokens;
        
        while (scanner.hasNextLine() && !done) {
            String line = scanner.nextLine();
            tokens = line.split(" ");
            //relation name
            if (tokens[0].equals("@relation")) {
                relationName = tokens[1];
            } //dealing with the attribute tag
            else if (tokens[0].equals("@attribute")) {
                String attributeName = tokens[1].trim();
                attributeNames.add(attributeName);
                Attribute a;
                //if the attribute is numeric
                if (tokens[2].equals("NUMERIC")) {
                    a = new Attribute(attributeName);
                    
                } else {//if the attribute is nominal,read the values
                    FastVector my_nominal_values = new FastVector();
                    tokens = line.split("[{}]|[,]");
                    for (int i = 1; i < tokens.length; i++) {
                        my_nominal_values.addElement(tokens[i].trim());
                    }
                    a = new Attribute(attributeName, my_nominal_values);
                }
                wekaAttributes.addElement(a);
                
            } else if (tokens[0].equals("@data")) {
                //read the data
                readDataRecords(scanner);
                done = true;
            } else if (tokens[0].equals("@secure")) {
                // does the attribute exist?
                if (!attributeNames.contains(tokens[1])) {
                    System.out.println("Error - secure tag " + tokens[1] + 
                            " does not match any attribute.");
                } else {
                    secureAttributes.add(tokens[1]);
                }
            }
        }
        //set class to the attribute before security code
        instances.setClassIndex(attributeNames.indexOf("SecurityCode") - 1);
        System.out.println("Done.");
        System.out.printf("There are %d instances in the original file\n",
                instances.numInstances());
    }
    
    /**
     * Performs the data fly algorithm that provides k anonymity
     * @throws Exception generated by weka when using one of its filter
     */
    public void datafly() throws Exception {
        System.out.println("Datafly - starting...");
        System.out.printf("Trying to acheive %d anonymity\n",k_anonymity_constant);
        //create copies of quais attributes
        copyAttributes(secureAttributes);
        //for each numeric quasi id, what is the current number of bins
        HashMap<String, Integer> attributeBins = new HashMap<String, Integer>();
        //stores the quais id set and their frequency in the data set
        HashMap<String, Integer> quasiFreq = new HashMap<String, Integer>();

        // set up the initial bins to be the sqrt of the number of instnaces
        for (String attributeName : secureAttributes) {
            attributeBins.put(attributeName, 
                    (int) Math.sqrt(((double) instances.numInstances())));
        }
        StringBuilder outputMessage=new StringBuilder();
        boolean done = false;
        int iterationCount = 1;
        do {
            outputMessage.append(String.format("\t\tDatafly - iteration count: "
                    + "%d\n", iterationCount));
            iterationCount++;
            //calculate the frequncy of the quasi id in the data sets
            calculateQuasiFrequencies(quasiFreq);
            
            int numRecords = 0;

            //count the number of records that don't meet k anonymity
            for (String key : quasiFreq.keySet()) {
                if (quasiFreq.get(key) < k_anonymity_constant) {
                    numRecords = numRecords + quasiFreq.get(key);
                }
            }
            //if the number of records is <= the k constant
            if (numRecords <= k_anonymity_constant) {
                done = true;
                outputMessage.append(String.format("Acheived %d anonymity\n",k_anonymity_constant));
            }else{
                outputMessage.append(String.format("%d records fail to meet %d anonymity\n",numRecords,k_anonymity_constant));
            }
            
            if (!done) {
                String maxQuasiAttributeName = getAttributeWithMostDistinctValues();

                // create a fresh copy
                copyAttribute(maxQuasiAttributeName);

                // grab the new copy, send it to be discretized
                Attribute maxQuasiAttribute = instances.attribute("Copy of " + 
                        maxQuasiAttributeName);
                int bins = attributeBins.get(maxQuasiAttributeName);
                DGHbinning(maxQuasiAttribute, bins);
                outputMessage.append(String.format("%s attribute has most "
                        + "distinct records. New Bin size:%d\n",
                        maxQuasiAttributeName,bins));
                
                
                // update the number of bins for the next iteration
                bins = (int) (bins * 0.9);
                attributeBins.put(maxQuasiAttributeName, bins);
            }

        } while (!done);
        //suppress records that don't meet k anonymity
        int suppressedRecords = suppressRecords(quasiFreq);
        outputMessage.append(String.format("Suppresed Records: %d\n", 
                suppressedRecords));
        System.out.printf("Suppresed Records: %d\n", suppressedRecords); 
        //restore the relation name
        deleteOriginalColumns();
        //remove the secure attribute
        removeAttribute("SecurityCode");
        //reset the realtion name
        instances.setRelationName(relationName);
        //print detailed log message
        //System.out.println(outputMessage.toString());
        System.out.println("Datafly - ended...");
    }

    /**
     * Suppress records that don't meet k-anonymity
     * 
     * @param quasiFreq  set of quasi id nd their frequencies
     * @return number of suppressed records
     */
    private int suppressRecords(HashMap<String, Integer> quasiFreq) {
        // suppression
        int suppressedRecords = 0;
        for (Enumeration e = instances.enumerateInstances(); 
                e.hasMoreElements();) {
            Instance instance = (Instance) e.nextElement();
            String quasiId=getQuasiId(instance);
            
            Integer freq = quasiFreq.get(quasiId);
            if (freq < k_anonymity_constant) {
                instance.setClassMissing();
                suppressedRecords = suppressedRecords + 1;
            }
        }
        instances.deleteWithMissingClass();
        return suppressedRecords;
    }

    /**
     * For the current dataset, what is the quasi attribute with 
     * the most distinct value 
     * @return name of attribute with most distinct value
     */
    private String getAttributeWithMostDistinctValues() {
        int max = 0;
        String maxQuasiAttributeName = null;
        for (String attributeName : secureAttributes) {
            int uniques = instances.numDistinctValues(instances.attribute
                    ("Copy of " + attributeName));
            if (uniques > max) {
                max = uniques;
                maxQuasiAttributeName = attributeName;
            }
        }
        return maxQuasiAttributeName;
    }

    /**
     * For the current dataset, calculate the frequencies of the 
     * dataset based on their quasi id 
     * 
     * @param quasiFreq frequencies based on quasi id
     */
    private void calculateQuasiFrequencies(HashMap<String, Integer> quasiFreq) {
        // clear and update  freq of records based on quasi id
        quasiFreq.clear();
        //for each instance in the dataset, calculate its quasi id and
        // update the frequncy count
        for (Enumeration e = instances.enumerateInstances(); 
                e.hasMoreElements();) {
            Instance instance = (Instance) e.nextElement();
            String quasiId = getQuasiId(instance);
            //current frquency of the quasi id
            Integer freq = quasiFreq.get(quasiId.toString());
            if (freq == null) {
                quasiFreq.put(quasiId.toString(), 1);
            } else {
                quasiFreq.put(quasiId.toString(), ++freq);
            }
        }
    }

    /**
     * Given an instance get the quasi id of that instance
     * 
     * @param instance  Instance to get quasi id
     * @return get quasi id string
     */
    private String getQuasiId(Instance instance) {
        StringBuilder quasiset = new StringBuilder();
        for (String attributeName : secureAttributes) {
            int index = instances.attribute("Copy of " + attributeName).index();
            Attribute a = instances.attribute(index);
            quasiset.append(instance.value(a));
        }
        return quasiset.toString();
    }
    
    /**
     * Discretize the attribute based on equal frequency and the number of bins
     * 
     * @param attribute  Attribute to discretize
     * @param numBins number of bins
     * @throws Exception if problem with binning
     */
    private void DGHbinning(Attribute attribute, int numBins) throws Exception {
        //System.out.println("DGH - performing on attribute " + attribute.name());
        Discretize filter = new Discretize();
        // get the index of the attribute
        int[] index = {attribute.index()};
        
        if (numBins < 3) {
            System.out.println(attribute.name() + " reduced to " + numBins + 
                    " bins.");
        }
        filter.setBins(numBins);
        filter.setUseEqualFrequency(true);
        filter.setAttributeIndicesArray(index);
        filter.setInputFormat(instances);
        instances = Filter.useFilter(instances, filter);
    }

    /**
     * saves the non private data instances  to the output file
     *
     * @throws Exception  if something goes wrong
     */
    public void saveArff() {
        try {
            BufferedWriter writer;
            System.out.println("There are " + instances.numInstances() +
                    " instances in the final output.");
            writer = new BufferedWriter(new FileWriter(outFile));
            writer.write(instances.toString());
            writer.newLine();
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            Logger.getLogger(Anonymize.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Apply the Remove filter to remove records that have asked that data not 
     * be mined
     */
    private void deletePrivateRecords() {
        try {
            RemoveWithValues filter = new RemoveWithValues();
            Attribute sec = instances.attribute("SecurityCode");
            int securityCodeIndex = sec.index() + 1;
            filter.setAttributeIndex(String.valueOf(securityCodeIndex));
            int index_private = sec.indexOfValue("2") + 1;
            filter.setNominalIndices(String.valueOf(index_private));
            filter.setInputFormat(instances);
            instances = RemoveWithValues.useFilter(instances, filter);
        } catch (Exception ex) {
            Logger.getLogger(Anonymize.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.printf("After deleing records with security code 2, "
                + "there are %d records\n",instances.numInstances());
    }

    /**
     * For all the data records create a instance and add it to an instances 
     * @param scanner location in file of data records
     */
    private void readDataRecords(Scanner scanner) {
        int num_records = 1000;
        instances = new Instances(relationName, wekaAttributes, num_records);
        while (scanner.hasNextLine()) {
            String[] line = scanner.nextLine().split(",");
            //create an instance
            Instance ins = new Instance(attributeNames.size());
            //for an instance, parse all its attribute values
            for (int attributeIndex = 0; attributeIndex < line.length; attributeIndex++) {
                Attribute a = (Attribute) wekaAttributes.elementAt(attributeIndex);
                if (a.isNumeric()) {
                    double d = Double.parseDouble(line[attributeIndex]);
                    ins.setValue(a, d);
                } else {
                    ins.setValue(a, line[attributeIndex]);
                }
            }
            //add to the instances
            instances.add(ins);
        }
        //set the classification class to the Donation code attribute
        instances.setClass(instances.attribute("DonorCode"));
    }
    
    /**
     * Creates a copy of the list of passed attribute names
     * 
     * @param quasiNames list of quasi names
     */
    private void copyAttributes(LinkedList<String> quasiNames) {
        for (String quasiName : quasiNames) {
            copyAttribute(quasiName);
        }
    }
    
    /**
     * Creates a copy of the passed attribute. If the attribute exists delete it
     * @param attributeName 
     */
    private void copyAttribute(String attributeName) {
        String copyAttributeName = "Copy of " + attributeName;
        removeAttribute(copyAttributeName);
        Copy copyFilter = new Copy();
        int attributeIndex = instances.attribute(attributeName).index();
        try {
            copyFilter.setAttributeIndicesArray(new int[]{attributeIndex});
            copyFilter.setInputFormat(instances);
            
            instances = Filter.useFilter(instances, copyFilter);
        } catch (Exception ex) {
            Logger.getLogger(Anonymize.class.getName()).log(Level.SEVERE, 
                    "Failed to copy attribute", ex);
        }
        
    }
    
    /**
     * Remove the passed attribute from the dataset
     */
    private void removeAttribute(String attribute) {
        Attribute att = instances.attribute(attribute);
        if (att != null) {
            int copyAttributeNameIndex = instances.attribute(attribute).index();
            instances.deleteAttributeAt(copyAttributeNameIndex);
        }
    }
    
    /**
     * Prints the list of attributes used in the dataset
     */
    private void printAttributes() {
        for (Enumeration e = instances.enumerateAttributes(); e.hasMoreElements();) {
            Attribute attribute = (Attribute) e.nextElement();
            System.out.println(attribute.name());
        }
    }
    
    /**
     * Main method. 
     * Preprocesses the passed file 
     * 
     * @param args 
     *      args[0]= arrf file to preprocess
     *      args[1]=location to save arrf file
     */
    public static void main(String args[]) {
        File in = new File(args[0]);
        File out = new File(args[1]);
        
        Anonymize pre = new Anonymize(in, out);
        pre.readFile();
        
        try {
            pre.deletePrivateRecords();
            pre.datafly();
            // prune
            pre.saveArff();
        } catch (Exception ex) {
            Logger.getLogger(Anonymize.class.getName()).log(Level.SEVERE, 
                    "Failed in preprocesssing hte file", ex);
        }
    }

    /**
     * Place the modified columns in the place of the original columns 
     * 1. Reorder the "original columns" to the end and the modified columns to the
     * original column location
     * 2. Delete the original columns
     * 3. Set the name of the copied columns to the original name
     */
    private void deleteOriginalColumns() {
        int[] columnOrder = new int[attributeNames.size() + secureAttributes.size()];
        int index = 0;
        for (String attributeName : attributeNames) {
            int columnNumber;
            //if the current column is a secure attribute
            // get the copy column's index
            if (secureAttributes.contains(attributeName)) {
                String copyName = "Copy of " + attributeName;
                columnNumber = instances.attribute(copyName).index();
                columnOrder[index] = columnNumber;
            } else {//get the column's index
                columnNumber = instances.attribute(attributeName).index();
                columnOrder[index] = columnNumber;
            }
            index = index + 1;
        }

        //place the original secure columns at the end
        for (String s : secureAttributes) {
            int columnNumber;
            columnNumber = instances.attribute(s).index();
            columnOrder[index] = columnNumber;
            index = index + 1;
        }

        //try reordering the columns
        Reorder reorderFilter = new Reorder();
        try {
            
            reorderFilter.setAttributeIndicesArray(columnOrder);
            reorderFilter.setInputFormat(instances);
            
            instances = Filter.useFilter(instances, reorderFilter);
        } catch (Exception ex) {
            Logger.getLogger(Anonymize.class.getName()).log(Level.SEVERE, 
                    "Failed when reordering the columns", ex);
        }

        //delete the original columns and change the copy column's name to 
        //the original
        for (String s : secureAttributes) {
            instances.deleteAttributeAt(instances.attribute(s).index());
            String copyName = "Copy of " + s;
            instances.renameAttribute(instances.attribute(copyName), s);
        }
    }
}
