package Racos.ObjectiveFunction;

import MPC.Automata;
import MPC.Location;
import MPC.Transition;
import Racos.Componet.Dimension;
import Racos.Componet.Instance;
import Racos.Tools.ValueArc;
import com.greenpineyu.fel.Expression;
import com.greenpineyu.fel.FelEngine;
import com.greenpineyu.fel.FelEngineImpl;
import com.greenpineyu.fel.context.FelContext;
import com.greenpineyu.fel.context.MapContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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
    private ArrayList<double[]> T12;
    private double[] T2;
    private ArrayList<HashMap<String,Double>> allParametersValues;
    int forwardCount;
    public double delta=0.05;

    private boolean sat = true;
    private double globalPenalty = 0;
    private double cerr = 0.01;
    private double penalty = 0;

    Random random=new Random();


    public Mission(ArrayList<String> commands,ArrayList<Automata> automatas){
        this.automatas=automatas;
        dim=new Dimension();
        this.path=new int[]{1,2,3};
        int paramSize = 0;
        XtarMAX=400;
        RtarMAX=180;
        VMAX=2000;
        boolean flag=false;//to label whether it is the first forward
        forwardCount=0;
        allParametersValues = new ArrayList<>();
        T12=new ArrayList<>();
        for(int i=0;i<commands.size();i++){
            String command=commands.get(i);
            if(command.contains("fast")) paramSize+=6;
            else if(command.contains("forward")) paramSize+=7;
            else{}//TODO -- other modes
        }

        dim.setSize(paramSize*2);

        int current_index=0;
        for(int k=0;k<automatas.size();k++){
            for(int i=0;i<commands.size();i++) {
                String command = commands.get(i);
                if(command.contains("fast")){
                    for(int j=current_index;j<current_index+3;j++){
                        //set X_tar(3)
                        dim.setDimension(j,0,XtarMAX,true);
                    }
                    current_index+=3;
                    for(int j=current_index;j<current_index+3;j++){
                        //set R_tar(3)
                        dim.setDimension(j,-RtarMAX,RtarMAX,true);
                    }
                    current_index+=3;

                }
                else if(command.contains("forward")&&!flag) {
                    flag=true;
                    for(int j=current_index;j<current_index+3;j++){
                        //set X_tar(3)
                        dim.setDimension(j,0, XtarMAX ,true);
                    }
                    current_index+=3;
                    for(int j=current_index;j<current_index+3;j++){
                        //set R_tar(3)
                        dim.setDimension(j,-RtarMAX,RtarMAX,true);
                    }
                    current_index+=3;
                    //set V_tar
                    dim.setDimension(current_index,0,VMAX,true);
                    current_index++;
                }
            }
        }


        fel = new FelEngineImpl();
        ctx = fel.getContext();
        valueArc = new ValueArc();

    }

    @Override
    public double getValue(Instance ins) {

        setAutomataByins(ins);

        allParametersValues = new ArrayList<>();
        double []args1 = new double[path.length];
        double []args2 = new double[path.length];
        args1[0]=(T12.get(0)[0]-T12.get(0)[1])/2;
        args1[1]=args1[0]+T12.get(0)[1];
        args1[2]=args1[1]+(T12.get(0)[0]-T12.get(0)[1])/2;

        checkInvarientsByODE(args1,args2);

        if(!sat) {
            if(penalty + globalPenalty == 0){
                //todo cfg file should have brackets
                System.out.println("penalty = 0 when unsat");
                System.exit(0);
            }
            double penAll = penalty + globalPenalty;
            if(penAll < valueArc.penAll) {
                valueArc.penalty = penalty;
                valueArc.globalPenalty = globalPenalty;
                valueArc.penAll = penAll;
            }
            return penAll;
        }
        return computeValue(args1);

    }
    public double computeValue(double []args){
        /*HashMap<String,Double> map = allParametersValues.get(allParametersValues.size() - 1);
        for(HashMap.Entry<String,Double> entry : map.entrySet()){
            ctx.set(entry.getKey(),entry.getValue());
        }
        ctx.set("target_x",automata.target_x);
        ctx.set("target_y",automata.target_y);
        Object obj = fel.eval(automata.obj_function);
        double value = 0;
        if(obj instanceof Double)
            value = (double)obj - 10000;
        else if(obj instanceof Integer){
            value = (int) obj - 10000;
        }
        else {
            System.err.println("error: result not of double!");
            System.out.println(obj);
            System.exit(-1);
        }*/
        double value = 0;
        if (value + 10000 < 0){
            System.exit(0);
        }
        if(value < valueArc.value){
            valueArc.value = value;
            valueArc.allParametersValues = allParametersValues.get(allParametersValues.size()-1);
            valueArc.args = args;
        }
        return value;
    }


    public HashMap<String, Double> cloneAllInitParametersValues(){
        HashMap<String, Double> newMap = new HashMap<>();
        for(int i=0;i<automatas.size();i++){
            for (Map.Entry<String, Double> entry : automatas.get(i).initParameterValues.entrySet()) {
                if(i==0)
                    newMap.put("a1_"+entry.getKey(), entry.getValue());
                else newMap.put("a2_"+entry.getKey(), entry.getValue());
            }
        }
        return newMap;

    }
    public boolean checkInvarientsByODE(double []args1,double []args2){
        double end1 = 0,end2=0;
        HashMap<String,Double> newMap = new HashMap<>();
        for(int locIndex = 0;locIndex < path.length;++locIndex){
            end1 = args1[locIndex];
            end2 = args2[locIndex];
            if(locIndex == 0) {
                newMap = cloneAllInitParametersValues();
            }else {
                newMap = (HashMap<String, Double>) allParametersValues.get(locIndex - 1).clone();
                for(int index=0;index<automatas.size();index++){
                    Transition transition = automatas.get(index).getTransitionBySourceAndTarget(path[locIndex - 1],path[locIndex]);
                    if(transition == null){
                        System.out.println("Found no transition");
                        System.exit(-1);
                    }
                    for(HashMap.Entry<String,String> entry : transition.assignments.entrySet()){
                        Object obj = fel.eval(entry.getValue());
                        double result = 0;
                        if(obj instanceof Integer)  result = (int)obj;
                        else if(obj instanceof Double) result = (double)obj;
                        else{
                            System.out.println("Not Double and Not Integer!");
                            System.out.println(entry.getValue());
                            System.exit(0);
                        }
                        if(index==0)
                            newMap.put("a1_"+entry.getKey(),result);
                        else newMap.put("a2_"+entry.getKey(),result);
                    }
                }
                //check assignments

            }
            if(end1==0){
                checkConstraints(newMap);
            }
            double step1 = newMap.get("a1_t_current"),step2=0;
            while(step1 < end1){
                //todo choose reasonable time-finish
                double delta1=delta;
                if(newMap.get("a1_t_current")+delta>end1){
                    delta1=end1-newMap.get("a1_t_current");
                }
                 newMap = computeValuesByFlow(newMap,locIndex,delta1);
                for(HashMap.Entry<String,Double> entry : newMap.entrySet()){
                    ctx.set(entry.getKey(),entry.getValue());
                }
                checkConstraints(newMap);
                for(int index=0;index<automatas.size();index++) {
                    for (int i = 0; i < automatas.get(i).locations.get(path[locIndex]).invariants.size(); ++i) {
                        String expression=index==0?"a1_"+automatas.get(i).locations.get(path[locIndex]).invariants.get((i)):"a2_"+automatas.get(i).locations.get(path[locIndex]).invariants.get((i));
                        boolean result = (boolean) fel.eval(expression);
                        if (!result) {
                            String invariant = index==0?"a1_"+automatas.get(i).locations.get(path[locIndex]).invariants.get(i):"a2_"+automatas.get(i).locations.get(path[locIndex]).invariants.get(i);

                            if (computePenalty(invariant, false) < cerr)
                                continue;
                            if (Double.isNaN(computePenalty(invariant, false))) {
                                sat = false;
                                penalty += 100000;
                            } else {
                                sat = false;
                                //System.out.println(invariant);
                                penalty += computePenalty(invariant, false);
                            }
                        }
                    }
                }
                step1 += delta1;
            }
            allParametersValues.add(newMap);
        }
        return true;
    }

    public HashMap<String,Double> computeValuesByFlow(HashMap<String,Double> parametersValues,int locIndex,double arg){

        HashMap<String,Double> tempMap = (HashMap<String, Double>) parametersValues.clone();
        for(int index=0;index<automatas.size();index++){
            Location location= automatas.get(index).locations.get(path[locIndex]);
            for(HashMap.Entry<String,Double> entry : parametersValues.entrySet()){
                ctx.set(entry.getKey(),entry.getValue());
            }
            for(HashMap.Entry<String,String> entry:location.flows.entrySet()){
                String expression;
                if(entry.getKey().contains("omega")){
                    if(entry.getValue().contains("50")){
                        expression = entry.getValue() ;
                    }
                    else expression = index==0? "a1_"+entry.getValue() : "a2_"+entry.getValue() ;

                    Object obj = fel.eval(expression);
                    double result;
                    if(obj instanceof Double)
                        result = (double)obj;
                    else if(obj instanceof Integer) {
                        result = (int) obj;
                    }
                    else if(obj instanceof Long){
                        result = ((Long)obj).doubleValue();
                    }
                    else {
                        result = 0;
                        System.out.println("Not Double and Not Integer!");
                        System.out.println(obj.getClass().getName());
                        System.out.println(obj);
                        System.out.println(location.flows.get(entry.getKey()));
                        System.exit(0);
                    }
                    double delta = result * arg;
                    String var_name=index==0? "a1_"+entry.getKey() : "a2_"+entry.getKey();
                    double res=parametersValues.get(var_name)+delta;
                    tempMap.put(var_name,res);
                    ctx.set(var_name,res);
                }

            }

            for(HashMap.Entry<String,String> entry:location.flows.entrySet()) {
                String expression;
                if (entry.getKey().contains("omega")) {
                }else {
                    if(isdigit(entry.getValue().trim())){
                        expression=entry.getValue();
                    }else expression = index==0? "a1_"+entry.getValue() : "a2_"+entry.getValue() ;
                    //double result=tempMap.get(expression);
                    Object obj = fel.eval(expression);

                    double result;
                    if(obj instanceof Double)
                        result = (double)obj;
                    else if(obj instanceof Integer) {
                        result = (int) obj;
                    }
                    else if(obj instanceof Long){
                        result = ((Long)obj).doubleValue();
                    }
                    else {
                        result = 0;
                        System.out.println("Not Double and Not Integer!");
                        System.out.println(obj.getClass().getName());
                        System.out.println(obj);
                        System.out.println(location.flows.get(entry.getKey()));
                        System.exit(0);
                    }
                    double delta = result * arg;
                    String var_name=index==0? "a1_"+entry.getKey() : "a2_"+entry.getKey();
                    double res=parametersValues.get(var_name) + delta;
                    tempMap.put(var_name,res);
                    ctx.set(var_name,res);

                }
            }
        }
        String s="a1_t_current";
        tempMap.put(s,tempMap.get(s)+arg);
        s="a2_t_current";
        tempMap.put(s,tempMap.get(s)+arg);

        return tempMap;
    }

    public boolean isdigit(String s){
        for(int i=0;i<s.length();i++){
            if((s.charAt(i)>'9'||s.charAt(i)<'0')&&s.charAt(i)!='.') return false;
        }
        return true;
    }
    public boolean checkConstraints(HashMap<String,Double> parametersValues){
        for(Map.Entry<String,Double> entry : parametersValues.entrySet()){
            ctx.set(entry.getKey(),entry.getValue());
        }
        if(automatas.get(0).forbiddenConstraints==null)
            return true;
        boolean result = (boolean)fel.eval(automatas.get(0).forbiddenConstraints);
        if(!result) return true;
        sat = false;
        globalPenalty += computeConstraintValue(automatas.get(0).forbiddenConstraints);
        return false;
    }
    public double computeConstraintValue(String constraint){
        int firstRightBracket = constraint.trim().indexOf(")");
        if(firstRightBracket != -1 && constraint.indexOf('&') == -1 && constraint.indexOf('|') == -1)
            return computePenalty(constraint.substring(constraint.indexOf('(')+1,constraint.lastIndexOf(")")),false);
        if(firstRightBracket != -1 && firstRightBracket != constraint.length()-1){
            for(int i = firstRightBracket;i < constraint.length();++i){
                if(constraint.charAt(i) == '&'){
                    int index = 0;
                    int numOfBrackets = 0;
                    int partBegin = 0;
                    double pen = 0;
                    while(index < constraint.length()){
                        if(constraint.indexOf(index) == '(')
                            ++numOfBrackets;
                        else if(constraint.indexOf(index) == ')')
                            --numOfBrackets;
                        else if(constraint.indexOf(index) == '&' && numOfBrackets==0){
                            String temp = constraint.substring(partBegin,index);
                            boolean result = (boolean)fel.eval(temp);
                            if(!result) return 0;
                            else pen+= computeConstraintValue(temp);
                            index = index + 2;
                            partBegin = index;
                            constraint = constraint.substring(index);
                            continue;
                        }
                        ++index;
                    }
                    return pen;
                }
                else if(constraint.charAt(i) == '|'){
                    int index = 0;
                    int numOfBrackets = 0;
                    int partBegin = 0;
                    double minPen = Double.MAX_VALUE;
                    while(index < constraint.length()){
                        if(constraint.indexOf(index) == '(')
                            ++numOfBrackets;
                        else if(constraint.indexOf(index) == ')')
                            --numOfBrackets;
                        else if(constraint.indexOf(index) == '|' && numOfBrackets==0){
                            String temp = constraint.substring(partBegin,index);
                            boolean result = (boolean)fel.eval(temp);
                            if(result){
                                minPen = (computeConstraintValue(temp) < minPen) ? computeConstraintValue(temp) : minPen;
                            }
                            index = index + 2;
                            partBegin = index;
                            constraint = constraint.substring(index);
                            continue;
                        }
                        ++index;
                    }
                    return minPen;
                }
            }
        }
        else{
            if(firstRightBracket != -1){
                constraint = constraint.substring(constraint.indexOf('(')+1,firstRightBracket);
            }
            if(constraint.indexOf('&') != -1){
                String []strings = constraint.split("&");
                double pen = 0;
                for(int i = 0;i < strings.length;++i){
                    if(strings[i].equals("")) continue;
                    boolean result = (boolean)fel.eval(strings[i]);
                    if(!result) return 0;
                    else pen += computeConstraintValue(strings[i]);
                }
                return pen;
            }
            else if(constraint.indexOf('|') != -1){
                String []strings = constraint.split("\\|");
                double minPen = Double.MAX_VALUE;
                for(int i = 0;i < strings.length;++i){
                    if(strings[i].equals("")) continue;
                    boolean result = (boolean) fel.eval(strings[i]);
                    if(!result) continue;
                    else minPen = (computeConstraintValue(strings[i]) < minPen) ? computeConstraintValue(strings[i]) : minPen;
                }
                return minPen;
            }
            else return computePenalty(constraint,false);
        }
        return 0;
    }
    private double computePenalty(String expression,boolean isConstraint){
        if(isConstraint && expression.indexOf("|") != -1)
            return computePenaltyOfConstraint(expression);

        String []strings;
        String bigPart = "",smallPart = "";
        strings = expression.split("<=|<|>=|>|==");
        Object obj1 = fel.eval(strings[0].trim());
        Object obj2 = fel.eval(strings[1].trim());
        double big = 0,small = 0;
        if(obj1 instanceof Double)
            big = (double)obj1;
        else if(obj1 instanceof Integer) {
            big = (int) obj1;
            //System.out.println(entry.getKey() + " " + entry.getValue());
        }
        else {
            System.out.println("Not Double and Not Integer!");
            System.out.println(expression);
            System.out.println(obj1);
            System.out.println(obj1.getClass().getName());
            System.out.println("here");
            System.exit(0);
        }
        if(obj2 instanceof Double)
            small = (double)obj2;
        else if(obj2 instanceof Integer) {
            small = (int) obj2;
        }
        else if(obj2 instanceof Long){
            small = ((Long)obj2).doubleValue();
        }
        else {
            small = 0;
            System.out.println("Not Double and Not Integer!");
            System.exit(0);
        }
        return Math.abs(big-small);
    }
    private double computePenaltyOfConstraint(String expression){//just one level
        String []expressions = expression.split("\\|");
        double result = Double.MAX_VALUE;
        for(String string:expressions){
            if(string.length()<=0)  continue;
            double temp = computePenalty(string,false);
            result = (temp < result) ? temp : result;
        }
        return result;
    }


    @Override
    public Dimension getDim() {
        return dim;
    }

    public void setAutomataByins(Instance ins){
        int current_index=0;

        HashMap<String,Double> newMap = new HashMap<>();
        InverseSolution IS=new InverseSolution();
        double[] X_tar=new double[3];
        double[] R_tar=new double[3];
        double[] current_Theta=new double[6];//current theta
        double[] current_X=new double[3];
        double[] current_R=new double[3];
        double[] delta_X=new double[3];
        double[] delta_R=new double[3];

        for(int i=0;i< automatas.size();i++) {
            double[] theta=new double[6];
            double[] X=new double[3];
            double[] R=new double[3];
            boolean flag=false;
            boolean first_fast=true;
            for(int locIndex = 0;locIndex < path.length;++locIndex){
                if(locIndex==0){
                    for(int index=0;index<6;index++){
                        current_Theta[index]=automatas.get(i).initParameterValues.get("theta"+Integer.toString(index+1));
                    }
                    for(int index=0;index<3;index++) {
                        current_X[index]=automatas.get(i).initParameterValues.get("X"+Integer.toString(index));
                        current_R[index]=automatas.get(i).initParameterValues.get("R"+Integer.toString(index));
                    }
                }
                else {
                    current_Theta=theta.clone();
                    current_X=X.clone();
                    current_R=R.clone();
                }
                String name=automatas.get(i).locations.get(locIndex+1).name;
                if(name.contains("fast")){
                    if(!first_fast) continue;
                    X_tar[0]=ins.getFeature(current_index);
                    X_tar[1]=ins.getFeature(current_index+1);
                    X_tar[2]=ins.getFeature(current_index+2);
                    current_index+=3;

                    R_tar[0]=ins.getFeature(current_index);
                    R_tar[1]=ins.getFeature(current_index+1);
                    R_tar[2]=ins.getFeature(current_index+2);
                    current_index+=3;

                    //Calculate inverse solution for theta
                    double[] theta_tar=IS.F_inverse(X_tar,R_tar,current_Theta);
                    while (theta_tar[0]==1000){
                        //current_index-=6;
                        /*for (int k = 0; k < 6; k++) {
                            ins.setFeature(i, dim.getRegion(i)[0]+random.nextDouble()*(dim.getRegion(i)[1]-dim.getRegion(i)[0]));
                        }*/
                        ins.setFeature(0+6*i,25);
                        ins.setFeature(1+6*i,214);
                        ins.setFeature(2+6*i,150);
                        ins.setFeature(3+6*i,0);
                        ins.setFeature(4+6*i,-25);
                        ins.setFeature(5+6*i,-90);

                        X_tar[0]=25;
                        X_tar[1]=214;
                        X_tar[2]=150;
                        R_tar[0]=0;
                        R_tar[1]=-25;
                        R_tar[2]=-90;

                        //Calculate inverse solution for theta
                        theta_tar=IS.F_inverse(X_tar,R_tar,current_Theta);
                    }
                    double[] omega_bar=IS.Solve_omega_bar(current_Theta,theta_tar);
                    T12.add(IS.Solve_T12(theta_tar,current_Theta));

                    //TODO calculate invarients/flow/guard... with theta_tar and omega_bar-finish
                    setFast(T12.get(i),i,locIndex);

                    theta=theta_tar.clone();
                    X=X_tar.clone();
                    R=R_tar.clone();
                    first_fast=false;



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

                        double[] theta_tar=IS.F_inverse(X_tar,R_tar,current_Theta);

                        for(int index=0;index<3;index++){
                            delta_X[index]=(X_tar[index]-current_X[index])/forwardCount;
                            delta_R[index]=(R_tar[index]-current_R[index])/forwardCount;
                            X_tar[index]=current_X[index]+delta_X[index];
                            R_tar[index]=current_R[index]+delta_R[index];
                        }

                        //TODO calculate invarients/flow/guard... with theta_tar and omega_bar
                        setForward();

                        theta=theta_tar.clone();
                        X=X_tar.clone();
                        R=R_tar.clone();
                    }


                }

            }
        }
    }
    void setFast(double[] T12,int Auto_index,int locIndex){
        //T12[0]=tamx,T12[0]=t2
        double t1=(T12[0]-T12[1])/2;
        double t2=t1+T12[1];
        double t3=t2+t1;
        if(locIndex!=0){
            Transition transition=automatas.get(Auto_index).getTransitionBySourceAndTarget(locIndex-1,locIndex);
            //todo fast mode is not the first mode
        }
        Location location=automatas.get(Auto_index).locations.get(locIndex+1);
        location.setVariant("t_current<="+t1,automatas.get(Auto_index).parameters);
        location.setFlow("theta1'=omega1 &amp; omega1'= 500 &amp; theta2'=omega2 &amp; omega2'= 500&amp; theta3'=omega3 &amp; omega3'= 500&amp; theta4'=omega4 &amp; omega4'= 500&amp; theta5'=omega5 &amp; omega5'= 500&amp;theta6'=omega6&amp; omega6'= 500 ",automatas.get(Auto_index).parameters);
        Transition transition=automatas.get(Auto_index).getTransitionBySourceAndTarget(locIndex+1,locIndex+2);
        transition.setGuard("t=="+t1,automatas.get(Auto_index).parameters);

        location=automatas.get(Auto_index).locations.get(locIndex+2);
        location.setVariant("t_current<="+t2,automatas.get(Auto_index).parameters);
        location.setFlow("theta1'= "+500*t1+"&amp;theta2'= "+500*t1+ "&amp;theta3'= "+500*t1+"&amp;theta4'= "+500*t1+"&amp;theta5'= "+500*t1+"theta0'= "+500*t1,automatas.get(Auto_index).parameters);
        transition=automatas.get(Auto_index).getTransitionBySourceAndTarget(locIndex+2,locIndex+3);
        transition.setGuard("t=="+t2,automatas.get(Auto_index).parameters);

        location=automatas.get(Auto_index).locations.get(locIndex+3);
        location.setVariant("t_current<="+t3,automatas.get(Auto_index).parameters);
        location.setFlow("theta1'=omega1 &amp; omega1'= -500 &amp; theta2'=omega2 &amp; omega2'= -500&amp; theta3'=omega3 &amp; omega3'= -500&amp; theta4'=omega4 &amp; omega4'= -500&amp; theta5'=omega5 &amp; omega5'= -500&amp;theta6'=omega6&amp; omega6'= -500 ",automatas.get(Auto_index).parameters);

    }
    void setForward(){

    }


}
