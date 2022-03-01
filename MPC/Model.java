package MPC;

import MPC.tools.Fel_ExpressionProc;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Model {
    public String forbiddenConstraints;
    public ArrayList<Automata> automatas;
    File output;
    BufferedWriter bufferedWriter;

    //public Map<String,Automata> system;
    public Model(String modelFileName, String cfgFileName){
        processModelFile(modelFileName);
        processCFGFile(cfgFileName);
    }

    void processModelFile(String modelFileName) {
        File modelFile = new File(modelFileName);
        BufferedReader reader = null;
        automatas=new ArrayList<>();
        try {
            reader = new BufferedReader(new FileReader(modelFile));
            String tempLine = null;
            while ((tempLine = reader.readLine()) != null) {
                Automata temp = new Automata();
                if (tempLine.indexOf("<component") != -1){
                    String[] string_temp = tempLine.split("\"");
                    temp.name=string_temp[1];
                    temp.locations = new HashMap<>();
                    temp.transitions = new ArrayList<>();
                    temp.parameters = new ArrayList<>();
                    tempLine = reader.readLine();
                    while (tempLine.indexOf("</component>") == -1){
                        if (tempLine.indexOf("<param") != -1) { // paramater definition
                            while (true) {
                                String[] strings = tempLine.split("\"");
                                if (strings[3].equals("real"))
                                    temp.parameters.add(strings[1]);
                                tempLine = reader.readLine();
                                if (tempLine.indexOf("<para") == -1) {
                                    temp.parametersSort();
                                    break;
                                }
                            }
                        }
                        if (tempLine.indexOf("<location") != -1) { // location definition
                            String[] strings = tempLine.split("\"");
                            //ID stores in strings[1]
                            Location location = new Location(Integer.parseInt(strings[1]), strings[3]);
                            tempLine = reader.readLine();
                            while (tempLine.indexOf("</location>") == -1) {//the end of this location
                                int beginIndex, endIndex;
                                if (tempLine.indexOf("<invar") != -1) {
                                    while (tempLine.indexOf("</invar") == -1) {
                                        if (tempLine.indexOf("<invar") != -1) {
                                            beginIndex = tempLine.indexOf("<invar") + 11;
                                            tempLine = tempLine.substring(beginIndex).trim();
                                        }
                                        location.setVariant(tempLine,temp.parameters);
                                        tempLine = reader.readLine();
                                    }
                                    if (tempLine.indexOf("<invar") != -1) {
                                        beginIndex = tempLine.indexOf("<invar") + 11;
                                        endIndex = tempLine.indexOf("</invar");
                                        tempLine = tempLine.substring(beginIndex, endIndex).trim();
                                    } else {
                                        endIndex = tempLine.indexOf("</invar");
                                        tempLine = tempLine.substring(0, endIndex).trim();
                                    }
                                    location.setVariant(tempLine, temp.parameters);
                                }
                                if (tempLine.indexOf("<flow>") != -1) {
                                    while (tempLine.indexOf("</flow>") == -1) {
                                        if (tempLine.indexOf("<flow>") != -1) {
                                            beginIndex = tempLine.indexOf("<flow>") + 6;
                                            tempLine = tempLine.substring(beginIndex).trim();
                                        }
                                        location.setFlow(tempLine, temp.parameters);
                                        tempLine = reader.readLine();
                                    }
                                    if (tempLine.indexOf("<flow>") != -1) {
                                        beginIndex = tempLine.indexOf("<flow>") + 6;
                                        endIndex = tempLine.indexOf("</flow>");
                                        tempLine = tempLine.substring(beginIndex, endIndex).trim();
                                    } else {
                                        endIndex = tempLine.indexOf("</flow>");
                                        tempLine = tempLine.substring(0, endIndex).trim();
                                    }
                                    location.setFlow(tempLine, temp.parameters);
                                }
                                tempLine = reader.readLine();
                            }
                            temp.locations.put(location.getNo(), location);
                            tempLine = reader.readLine();
                        }
                        if (tempLine.indexOf("<transition") != -1) { // transition definition
                            String[] strings = tempLine.split("\"");
                            int source = Integer.parseInt(strings[1]);
                            int target = Integer.parseInt(strings[3]);
                            Transition transition = new Transition(source, target);
                            temp.locations.get(source).addNeibour(target);
                            tempLine = reader.readLine(); // guard
                            while (tempLine.indexOf("</transi") == -1) {
                                int beginIndex, endIndex;
                                if (tempLine.indexOf("<guard>") != -1) {
                                    beginIndex = tempLine.indexOf("<guard>") + 7;
                                    endIndex = tempLine.indexOf("</guard>");
                                    String guard = tempLine.substring(beginIndex, endIndex).trim();
                                    transition.setGuard(guard, temp.parameters);
                                }
                                if (tempLine.indexOf("<assignment>") != -1) {
                                    beginIndex = tempLine.indexOf("<assignment>") + 12;
                                    endIndex = tempLine.indexOf("</assignment>");
                                    String assignment = tempLine.substring(beginIndex, endIndex).trim();
                                    transition.setAssignment(assignment, temp.parameters);
                                }
                                tempLine = reader.readLine();
                            }
                            temp.transitions.add(transition);
                            tempLine = reader.readLine();
                        }
                        if (tempLine.indexOf("<bind") != -1){
                            String[] strings = tempLine.split("\"");
                            //system=new HashMap<>();
                            for(int i=0;i<automatas.size();i++){
                                if(automatas.get(i).name.equals(strings[1])){
                                    automatas.get(i).labelasName=strings[3];
                                    //system.put(strings[3],automatas.get(i));
                                    break;
                                }
                            }
                            tempLine = reader.readLine();
                            if(tempLine.indexOf("</bind>")!=-1){
                                tempLine = reader.readLine();
                            }
                        }
                    }
                    if(tempLine.indexOf("</component>") != -1 &&temp.parameters.size()!=0){
                        automatas.add(temp);
                    }
                }

            }
            } catch (FileNotFoundException e) {
                System.out.println("File not found" + '\n' + e.getMessage());
            } catch (IOException e) {
                System.out.println("IO Exception" + '\n' + e.getMessage());
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        System.out.println("IO Exception" + '\n' + e.getMessage());
                    }
                }
            }
    }
    void processCFGFile(String cfgFileName) {
        File cfgFile = new File(cfgFileName);
        BufferedReader reader = null;
        for(int i=0;i<automatas.size();i++){
            automatas.get(i).initParameterValues=new HashMap<>();
        }
        try {
            reader = new BufferedReader(new FileReader(cfgFile));
            String tempLine = null;
            while ((tempLine = reader.readLine()) != null) {
                if (tempLine.charAt(0) == '#')
                    continue;
                if (tempLine.startsWith("initially")) {
                    String[] strings = tempLine.split("\"");
                    setInitParameterValues(strings[1]);
                }
                if (tempLine.startsWith("forbidden")) {
                    String[] strings = tempLine.split("\"");
                    strings[1] = strings[1].replace("pow", "$(Math).pow");
                    strings[1] = strings[1].replace("sin", "$(Math).sin");
                    strings[1] = strings[1].replace("cos", "$(Math).cos");
                    strings[1] = strings[1].replace("tan", "$(Math).tan");
                    strings[1] = strings[1].replace("sqrt", "$(Math).sqrt");
                    forbiddenConstraints = strings[1];
                }
            }

        } catch (FileNotFoundException e) {
            System.out.println("File not found" + '\n' + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO Exception" + '\n' + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    System.out.println("IO Exception" + '\n' + e.getMessage());
                }
            }
        }
    }

    Automata findAutomata(String labelname){
        for(int i=0;i<automatas.size();i++){
            if(automatas.get(i).labelasName.equals(labelname))
                return automatas.get(i);
        }
        return null;
    }

    void setInitParameterValues(String initValues){
        String[] strings = initValues.split("&");
        for (int i = 0; i < strings.length; ++i) {
            String[] temp = strings[i].split("==");
            if(temp[0].indexOf('.')!=-1){
                String[] assign_object=temp[0].trim().split("\\.");
                Automata A=findAutomata(assign_object[0]);
                if (temp[1].indexOf('[') != -1){
                    int firstIndex = temp[1].indexOf("[");
                    int lastIndex = temp[1].indexOf("]");
                    String[] bounds = temp[1].substring(firstIndex + 1, lastIndex).trim().split(",");
                    double lowerbound = Double.parseDouble(bounds[0].trim());
                    double upperbound = Double.parseDouble(bounds[1].trim());
                    if (A.rangeParameters == null) A.rangeParameters = new ArrayList<>();
                    A.rangeParameters.add(new RangeParameter(assign_object[1], lowerbound, upperbound));
                }
                else A.initParameterValues.put(assign_object[1], Double.parseDouble(temp[1].trim()));

            } else if(temp[0].trim().indexOf("loc(")!=-1){
                int beginIndex=temp[0].indexOf("loc(");
                int endIndex=temp[0].indexOf(")");
                String label_name=temp[0].substring(beginIndex+4,endIndex).trim();
                Automata A=findAutomata(label_name);
                A.initLocName=temp[1].trim();
                for (Map.Entry<Integer, Location> entry : A.locations.entrySet()) {
                    //System.out.println(allParametersValues.size());
                    if (entry.getValue().name.equals(A.initLocName)) {
                        A.initLoc = entry.getKey();
                        break;
                    }
                }
            }
        }
    }

    void checkAutomata(){
        for(int i=0;i<automatas.size();i++){
            automatas.get(i).bufferedWriter=bufferedWriter;
            automatas.get(i).checkAutomata();
        }
    }

    public static void main(String[] args) {
        configUtil config = new configUtil();
        String prefix = new String("models/" + config.get("system") + "_" + config.get("mission"));
        String modelFile = prefix + ".xml";
        String cfgFile = prefix + ".cfg";

        double currentTime = System.currentTimeMillis();
        Model model=new Model(modelFile,cfgFile);

        model.output= new File("logs.txt");
        try {
            model.bufferedWriter= new BufferedWriter(new FileWriter(model.output));
            model.checkAutomata();
            model.bufferedWriter.close();
            int maxPathSize = Integer.parseInt(config.get("bound"));

            File file = new File("result.txt");
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));

        } catch (Exception e) {
            e.printStackTrace();
        }

        double endTime = System.currentTimeMillis();
        System.out.println("Time cost :" + (endTime - currentTime) / 1000 + " seconds");


    }
}
