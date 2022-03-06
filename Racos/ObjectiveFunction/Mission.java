package Racos.ObjectiveFunction;

import MPC.Automata;
import MPC.Location;
import Racos.Componet.Dimension;
import Racos.Componet.Instance;
import Racos.Tools.ValueArc;
import com.greenpineyu.fel.FelEngine;
import com.greenpineyu.fel.FelEngineImpl;
import com.greenpineyu.fel.context.FelContext;

import java.util.ArrayList;
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


    public Mission(ArrayList<Automata> automatas,int []path){
        this.automatas=automatas;
        dim=new Dimension();
        this.path=path;
        int paramSize = 0;
        XtarMAX=0.5;
        RtarMAX=1;
        VMAX=1000;
        for(int i=0;i< automatas.size();i++){
            for(Map.Entry<Integer, Location > entry:automatas.get(i).locations.entrySet()){
                String name=entry.getValue().name;
                //fast mode needs X_tar(3) and R_tar(3)
                if(name.contains("fast")){
                    paramSize+=6;
                }
                else if(name.contains("forward")) {
                    //forward mode needs X_tar(3) and R_tar(3) and V(1)
                    paramSize+=7;
                }
                //TODO -- other modes
            }
        }
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
                else if(name.contains("forward")) {
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

        for(int i=0;i< automatas.size();i++) {
            for(Map.Entry<Integer, Location > entry:automatas.get(i).locations.entrySet()){
                String name=entry.getValue().name;
                if(name.contains("fast")){
                    double[] X_tar=new double[3];
                    X_tar[0]=ins.getFeature(current_index);
                    X_tar[1]=ins.getFeature(current_index+1);
                    X_tar[2]=ins.getFeature(current_index+2);
                    current_index+=3;

                    double[] R_tar=new double[3];
                    R_tar[0]=ins.getFeature(current_index);
                    R_tar[1]=ins.getFeature(current_index+1);
                    R_tar[2]=ins.getFeature(current_index+2);
                    current_index+=3;

                    double[] Theta=new double[6];
                    int index=0;
                    for(double v:automatas.get(i).initParameterValues.values()){
                        Theta[index]=v;
                        index++;
                    }
                    //Calculate inverse solution for theta
                    InverseSolution IS=new InverseSolution();
                    double[] theta_tar=IS.F_inverse(X_tar,R_tar,Theta);
                    double[] omega_bar=IS.Solve_omega_bar(Theta,theta_tar);







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
