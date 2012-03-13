//import statements
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.instance.RemoveWithValues;


/**
 * TrainingGenerator reads information from an arrf file and divide
 * the data to a training and testing set based on a user defined percentage 
 * 
 * @author Eitan Romanoff ear7631@cs.rit.edu
 * @author  Nidhin Pattaniyil ntp5633@cs.rit.edu
 */
public class TrainingGenerator {

    //stores the instances split by their classification
    private ArrayList<Instances> instancesByClassificiation;
    //set of all intances 
    private Instances instances;
    //contains the training instances
    private Instances trainingInstances;
    //contains the validation instances
    private Instances validatationInstnaces;
    //path to save the training data
    private String trainingSetPath;
    //p[ath to save the testing data
    private String testingSetPath;
    
    /**
     * Creates an instance of the TrainingGenerator
     * 
     * @param instances  instances to split among training and validation
     * @param randomize  should the data be randomized before splitting
     * @param trainingSetPath  the destination path of the training instances
     * @param testingSetPath  the destination path of the testing instances
     */
    public TrainingGenerator(Instances instances,boolean randomize,String 
            trainingSetPath,String testingSetPath){
        instancesByClassificiation = new ArrayList<Instances>();
        this.instances=instances;
        trainingInstances=new Instances(instances);
        trainingInstances.delete();
        validatationInstnaces =new Instances(instances);
        validatationInstnaces.delete();
        this.trainingSetPath=trainingSetPath;
        this.testingSetPath=testingSetPath;
        if(randomize){
            instances.randomize(new Random());
        }
        System.out.printf("Instances in the original file:%d\n",
                instances.numInstances());
       
    }
    /**
     * Split the instances by the their class
     */
    private  void splitInstances() {
        RemoveWithValues filter;
        Attribute sec = instances.attribute("DonorCode");
        String attributeindex = "" + (sec.index() + 1);
        //enumerate through the nominal values of the class attribute
        for (Enumeration e = sec.enumerateValues(); e.hasMoreElements();) {
            String attributeValue = (String) e.nextElement();
            int index = sec.indexOfValue(attributeValue);
            filter = new RemoveWithValues();
            //remove  instances that don't have this attribute's nominal value
            filter.setAttributeIndex(attributeindex);
            filter.setNominalIndicesArr(new int[]{index});
            filter.setInvertSelection(true);
            try {
                filter.setInputFormat(instances);
                Instances splitInstances;
                splitInstances = Filter.useFilter(instances, filter);
                instancesByClassificiation.add(splitInstances);
            } catch (Exception ex) {
                Logger.getLogger(TrainingGenerator.class.getName()).log
                        (Level.SEVERE, "Failing in splitting instances", ex);
            }

        }
    }
    
    /**
     * The number of instances that should be reserved for the training data
     * from each class 
     * 
     * @param percentage instances to be in the training
     */
    private void generateTrainingAndValidationData(double percentage) {
        int pct=(int)(percentage*100);
        System.out.printf("%d%% of the data is assigned to the test set\n",pct);
        splitInstances();
        trainingInstances = new Instances(instances);
        trainingInstances.delete();
        validatationInstnaces = new Instances(instances);
        validatationInstnaces.delete();
        Instances tmp;
        for (Instances in : instancesByClassificiation) {
            int trianingCount=(int)(in.numInstances()*percentage);
            int count=0;
            for(Enumeration e=in.enumerateInstances();e.hasMoreElements();){
                Instance i=(Instance)e.nextElement();
                if(count<=trianingCount){
                    trainingInstances.add(i);
                }else{
                    validatationInstnaces.add(i);
                }
                count++;
            }
         }
        System.out.printf("Instances in the training Data:%d\n",
                trainingInstances.numInstances());
        System.out.printf("Instances in the validation Data:%d\n",
                validatationInstnaces.numInstances());
        saveArff(trainingSetPath,trainingInstances);
        saveArff(testingSetPath,validatationInstnaces);

    }

    /**
     * saves the non private data instances  to the output file
     *
     * @throws Exception  if something goes wrong
     */
    private void saveArff(String filepath, Instances instances) {
        try {
            BufferedWriter writer;
            writer = new BufferedWriter(new FileWriter(filepath));
            writer.write(instances.toString());
            writer.newLine();
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            Logger.getLogger(Anonymize.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
   
    /**
     * Main Method
     *      Creates an instance of the TrainingGEenretaro and 
     * creates a training and test set
     * @param args 
     *      args[0]: arff file of all data
     *      args[1]: location to save train instances
     *      args[2]: location to save test instances
     *      args[3]: the split percentage for training and validation
     */
    public static void main(String[] args) {

        try {
            BufferedReader reader = new BufferedReader(new FileReader(args[0]));
            //load all the infromation from the arrf file
            Instances instances = new Instances(reader);
            //initialize the instances and pass in the location of the arff 
            // file and deestion path fro training and test set
            TrainingGenerator trainingGenerator=new TrainingGenerator(instances,
                    true,args[1],args[2]);
            //create the training and test data set
            trainingGenerator.generateTrainingAndValidationData
                    (Double.parseDouble(args[3]));
        } catch (IOException ex) {
            Logger.getLogger(TrainingGenerator.class.getName()).log(Level.SEVERE, 
                    "An io exception when parsing the file", ex);
        } catch (Exception ex) {
            Logger.getLogger(TrainingGenerator.class.getName()).log(Level.SEVERE, 
                    "An exception was thrown by weka", ex);
        }
    }
    
}
