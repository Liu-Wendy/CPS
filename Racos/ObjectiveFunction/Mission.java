package Racos.ObjectiveFunction;

import MPC.Automata;
import MPC.Location;
import MPC.Transition;
import Racos.Componet.Dimension;
import Racos.Componet.Instance;
import Racos.Tools.ValueArc;
import com.greenpineyu.fel.FelEngine;
import com.greenpineyu.fel.FelEngineImpl;
import com.greenpineyu.fel.context.FelContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Mission implements Task{
    private Dimension dim;//the number of all parameters
    public ArrayList<Automata> automatas;
    private FelEngine fel;
    private FelContext ctx;
    public ValueArc valueArc;
    private int []path;
    private double XtarMAX;
    private double RtarMAX;
    private double VMAX;
    private ArrayList<ArrayList<HashMap<String,Double>>> allParametersValues;
    int forwardCount;

    public Mission(ArrayList<Automata> automatas,int []path){
        this.automatas=automatas;
        dim=new Dimension();
        this.path=path;
        int paramSize = 0;
        XtarMAX=0.5;
        RtarMAX=1;
        VMAX=1000;
        boolean flag=false;//to label whether it is the first forward
        forwardCount=0;
        for(int i=0;i< automatas.size();i++){
            flag=false;
            for(Map.Entry<Integer, Location > entry:automatas.get(i).locations.entrySet()){
                String name=entry.getValue().name;
                //fast mode needs X_tar(3) and R_tar(3)
                if(name.contains("fast")){
                    paramSize+=6;
                }
                else if(name.contains("forward")) {
                    forwardCount++;
                    if(!flag){
                        flag=true;
                        //forward mode needs X_tar(3) and R_tar(3) and V(1)
                        paramSize+=7;
                    }
                }
                //TODO -- other modes
            }
        }
        flag=false;
        dim.setSize(paramSize);
        int current_index=0;
        for(int i=0;i< automatas.size();i++){
            for(Map.Entry<Integer, Location > entry:automatas.get(i).locations.entrySet()){
                String name=entry.getValue().name;
                //fast mode needs X_tar(3) and R_tar(3)
                if(name.contains("fast")){
                    for(int j=current_index;j<current_index+3;j++){
                        //set X_tar(3)
                        dim.setDimension(j,0,XtarMAX,true);
                    }
                    current_index+=3;
                    for(int j=current_index;j<current_index+3;j++){
                        //set R_tar(3)
                        dim.setDimension(j,0,RtarMAX,true);
                    }
                    current_index+=3;

                }
                else if(name.contains("forward")&&!flag) {
                    flag=true;
                    for(int j=current_index;j<current_index+3;j++){
                        //set X_tar(3)
                        dim.setDimension(j,0, XtarMAX ,true);
                    }
                    current_index+=3;
                    for(int j=current_index;j<current_index+3;j++){
                        //set R_tar(3)
                        dim.setDimension(j,0,RtarMAX,true);
                    }
                    current_index+=3;
                    //set V_tar
                    dim.setDimension(current_index,0,VMAX,true);
                    current_index++;
                }
                //TODO -- other modes
            }
        }

        fel = new FelEngineImpl();
        ctx = fel.getContext();
        valueArc = new ValueArc();

    }

    @Override
    public double getValue(Instance ins) {
        int current_index=0;
        allParametersValues = new ArrayList<>();
        HashMap<String,Double> newMap = new HashMap<>();
        InverseSolution IS=new InverseSolution();
        double[] X_tar=new double[3];
        double[] R_tar=new double[3];
        double[] Theta=new double[6];//current theta
        double[] current_X=new double[3];
        double[] current_R=new double[3];
        double[] delta_X=new double[3];
        double[] delta_R=new double[3];

        for(int i=0;i< automatas.size();i++) {
            boolean flag=false;
            for(int locIndex = 0;locIndex < path.length;++locIndex){
                if(locIndex == 0)
                    newMap = automatas.get(i).duplicateInitParametersValues();
                else {
                    newMap = (HashMap<String, Double>) allParametersValues.get(i).get(locIndex - 1).clone();
                }
                String name=automatas.get(i).locations.get(locIndex+1).name;
                if(name.contains("fast")){
                    X_tar[0]=ins.getFeature(current_index);
                    X_tar[1]=ins.getFeature(current_index+1);
                    X_tar[2]=ins.getFeature(current_index+2);
                    current_index+=3;

                    R_tar[0]=ins.getFeature(current_index);
                    R_tar[1]=ins.getFeature(current_index+1);
                    R_tar[2]=ins.getFeature(current_index+2);
                    current_index+=3;

                    for(int index=0;index<6;index++){
                        String tmp= Integer.toString (index+1);
                        Theta[index]=newMap.get("theta"+tmp);
                    }
                    //Calculate inverse solution for theta
                    double[] theta_tar=IS.F_inverse(X_tar,R_tar,Theta);
                    double[] omega_bar=IS.Solve_omega_bar(Theta,theta_tar);


                    //TODO calculate invarients/flow/guard... with theta_tar and omega_bar

                    allParametersValues.get(i).get(locIndex).put("X0",X_tar[0]);
                    allParametersValues.get(i).get(locIndex).put("X1",X_tar[1]);
                    allParametersValues.get(i).get(locIndex).put("X2",X_tar[2]);
                    allParametersValues.get(i).get(locIndex).put("R0",R_tar[0]);
                    allParametersValues.get(i).get(locIndex).put("R1",R_tar[1]);
                    allParametersValues.get(i).get(locIndex).put("R2",R_tar[2]);

                }else if(name.contains("forward")){
                    if(!flag){
                        flag=true;
                        X_tar[0]=ins.getFeature(current_index);
                        X_tar[1]=ins.getFeature(current_index+1);
                        X_tar[2]=ins.getFeature(current_index+2);
                        current_index+=3;

                        R_tar[0]=ins.getFeature(current_index);
                        R_tar[1]=ins.getFeature(current_index+1);
                        R_tar[2]=ins.getFeature(current_index+2);
                        current_index+=3;

                        double V_tar=ins.getFeature(current_index);
                        current_index+=1;

                        //Theta contains current theta infomation
                        for(int index=0;index<6;index++){
                            String tmp= Integer.toString (index+1);
                            Theta[index]=newMap.get("theta"+tmp);
                        }
                        double[] theta_tar=IS.F_inverse(X_tar,R_tar,Theta);

                        for(int index=0;index<3;index++){
                            String tmp= Integer.toString (index);
                            current_X[index]=newMap.get("X"+tmp);
                            current_R[index]=newMap.get("R"+tmp);
                        }
                        for(int index=0;index<3;index++){
                            delta_X[index]=(X_tar[index]-current_X[index])/forwardCount;
                            delta_R[index]=(R_tar[index]-current_R[index])/forwardCount;
                            X_tar[index]=current_X[index]+delta_X[index];
                            R_tar[index]=current_R[index]+delta_R[index];
                        }

                        //TODO calculate invarients/flow/guard... with theta_tar and omega_bar

                        allParametersValues.get(i).get(locIndex).put("X0",X_tar[0]);
                        allParametersValues.get(i).get(locIndex).put("X1",X_tar[1]);
                        allParametersValues.get(i).get(locIndex).put("X2",X_tar[2]);
                        allParametersValues.get(i).get(locIndex).put("R0",R_tar[0]);
                        allParametersValues.get(i).get(locIndex).put("R1",R_tar[1]);
                        allParametersValues.get(i).get(locIndex).put("R2",R_tar[2]);
                    }


                }

            }
        }


        return 0;
    }

    @Override
    public Dimension getDim() {
        return dim;
    }


}
