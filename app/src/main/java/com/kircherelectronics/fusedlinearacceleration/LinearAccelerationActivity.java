package com.kircherelectronics.fusedlinearacceleration;



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.XYPlot;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import com.kircherelectronics.fusedlinearacceleration.plot.DynamicPlot;
import com.kircherelectronics.fusedlinearacceleration.plot.PlotColor;
import com.kircherelectronics.fusedlinearacceleration.sensor.AccelerationSensor;
import com.kircherelectronics.fusedlinearacceleration.sensor.GravitySensor;
import com.kircherelectronics.fusedlinearacceleration.sensor.GyroscopeSensor;
import com.kircherelectronics.fusedlinearacceleration.sensor.LinearAccelerationSensor;
import com.kircherelectronics.fusedlinearacceleration.sensor.MagneticSensor;
import com.kircherelectronics.fusedlinearacceleration.sensor.observer.AccelerationSensorObserver;
import com.kircherelectronics.fusedlinearacceleration.sensor.observer.LinearAccelerationSensorObserver;

/**
 * Uses a sensor fusion approach using the acceleration sensor, magnetic sensor
 * and gyroscope sensor via complementary filter to estimate the linear
 * acceleration of the device.
 *
 * @author Kaleb
 */
public class LinearAccelerationActivity extends Activity implements Runnable,
        OnTouchListener, LinearAccelerationSensorObserver,
        AccelerationSensorObserver {


    ArrayList<Double> accX = new ArrayList<Double>();

    ArrayList<Double> accXfilter = new ArrayList<Double>();
    ArrayList<Double> velXfilter = new ArrayList<Double>();
    ArrayList<Double> disXfilter = new ArrayList<Double>();

    ArrayList<Double> accY = new ArrayList<Double>();

    ArrayList<Double> accYfilter = new ArrayList<Double>();
    ArrayList<Double> velYfilter = new ArrayList<Double>();
    ArrayList<Double> disYfilter = new ArrayList<Double>();

    ArrayList<Float> timeDiff = new ArrayList<Float>();
    LineGraphSeries<DataPoint> series;
    LineGraphSeries<DataPoint> seriesKalman;
    LineGraphSeries<DataPoint> seriesVelcoity;
    private static final String tag = LinearAccelerationActivity.class
            .getSimpleName();

    // Indicate if the output should be logged to a .csv file
    private boolean logData = false;

    // Decimal formats for the UI outputs
    private DecimalFormat df;

    // Graph plot for the UI outputs
    private DynamicPlot dynamicPlot;

    // Outputs for the acceleration and LPFs
    private float[] acceleration = new float[3];
    private float[] linearAcceleration = new float[3];

    // Touch to zoom constants for the dynamicPlot
    private float distance = 0;
    private float zoom = 1.2f;
    private TextView tv_result;
    private TextView textView;


    // Icon to indicate logging is active
    private ImageView iconLogger;

    // The generation of the log output
    private int generation = 0;

    // Plot keys for the acceleration plot
    private int plotAccelXAxisKey = 0;
    private int plotAccelYAxisKey = 1;
    private int plotAccelZAxisKey = 2;

    // Plot keys for the LPF Wikipedia plot
    private int plotLinearAccelXAxisKey = 3;
    private int plotLinearAccelYAxisKey = 4;
    private int plotLinearAccelZAxisKey = 5;

    // Color keys for the acceleration plot
    private int plotAccelXAxisColor;
    private int plotAccelYAxisColor;
    private int plotAccelZAxisColor;

    // Color keys for the LPF Wikipedia plot
    private int plotLinearAccelXAxisColor;
    private int plotLinearAccelYAxisColor;
    private int plotLinearAccelZAxisColor;

    // Log output time stamp
    private long logTime = 0;

    // Plot colors
    private PlotColor color;

    private AccelerationSensor accelerationSensor;
    private GravitySensor gravitySensor;
    private GyroscopeSensor gyroscopeSensor;
    private MagneticSensor magneticSensor;
    private LinearAccelerationSensor linearAccelerationSensor;

    // Acceleration plot titles
    private String plotAccelXAxisTitle = "AX";
    private String plotAccelYAxisTitle = "AY";
    private String plotAccelZAxisTitle = "AZ";

    // LPF Wikipedia plot titles
    private String plotLinearAccelXAxisTitle = "lAX";
    private String plotLinearAccelYAxisTitle = "lAY";
    private String plotLinearAccelZAxisTitle = "lAZ";

    // Output log
    private String log;

    // Acceleration UI outputs
    private TextView xAxis;
    private TextView yAxis;
    private TextView zAxis;

    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_acceleration);

        View view = findViewById(R.id.plot_layout);
        view.setOnTouchListener(this);
        SensorManager manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        List<Sensor> sensorList = manager.getSensorList(Sensor.TYPE_ALL);



        // Create the graph plot
        XYPlot plot = (XYPlot) findViewById(R.id.plot_sensor);
        plot.setTitle("Acceleration");
        dynamicPlot = new DynamicPlot(plot);
        dynamicPlot.setMaxRange(11.2);
        dynamicPlot.setMinRange(-11.2);

        // Create the acceleration UI outputs
        xAxis = (TextView) findViewById(R.id.value_x_axis);
        yAxis = (TextView) findViewById(R.id.value_y_axis);
        zAxis = (TextView) findViewById(R.id.value_z_axis);

        // Create the logger icon
        iconLogger = (ImageView) findViewById(R.id.icon_logger);
        iconLogger.setVisibility(View.INVISIBLE);

        // Format the UI outputs so they look nice
        df = new DecimalFormat("#.##");

        linearAccelerationSensor = new LinearAccelerationSensor();
        accelerationSensor = new AccelerationSensor(this);
        gravitySensor = new GravitySensor(this);
        gyroscopeSensor = new GyroscopeSensor(this);
        magneticSensor = new MagneticSensor(this);

        // Initialize the plots
        initColor();
        initPlot();
        initGauges();

        handler = new Handler();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.acceleration, menu);
        return true;
    }

    /**
     * Event Handling for Individual menu item selected Identify single menu
     * item by it's id
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
                   // Log the data

                startDataLog();
                return true;

            // Log the data

    }

    @Override
    public void onPause() {
        super.onPause();

        accelerationSensor.removeAccelerationObserver(this);
        accelerationSensor.removeAccelerationObserver(linearAccelerationSensor);
        gravitySensor.removeGravityObserver(linearAccelerationSensor);
        gyroscopeSensor.removeGyroscopeObserver(linearAccelerationSensor);
        magneticSensor.removeMagneticObserver(linearAccelerationSensor);

        linearAccelerationSensor.removeLinearAccelerationObserver(this);

        if (logData) {


            writeLogToFile();
        }

        handler.removeCallbacks(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        handler.post(this);

        accelerationSensor.registerAccelerationObserver(this);
        accelerationSensor
                .registerAccelerationObserver(linearAccelerationSensor);
        gravitySensor.registerGravityObserver(linearAccelerationSensor);
        gyroscopeSensor.registerGyroscopeObserver(linearAccelerationSensor);
        magneticSensor.registerMagneticObserver(linearAccelerationSensor);

        linearAccelerationSensor.registerLinearAccelerationObserver(this);
    }

    /**
     * Pinch to zoom.
     */
    @Override
    public boolean onTouch(View v, MotionEvent e) {
        // MotionEvent reports input details from the touch screen
        // and other input controls.
        float newDist = 0;

        switch (e.getAction()) {

            case MotionEvent.ACTION_MOVE:

                // pinch to zoom
                if (e.getPointerCount() == 2) {
                    if (distance == 0) {
                        distance = fingerDist(e);
                    }

                    newDist = fingerDist(e);

                    zoom *= distance / newDist;

                    dynamicPlot.setMaxRange(zoom * Math.log(zoom));
                    dynamicPlot.setMinRange(-zoom * Math.log(zoom));

                    distance = newDist;
                }
        }

        return false;
    }

    @Override
    public void onAccelerationSensorChanged(float[] acceleration, long timeStamp) {
        // Get a local copy of the sensor values
        System.arraycopy(acceleration, 0, this.acceleration, 0,
                acceleration.length);
    }

    @Override
    public void onLinearAccelerationSensorChanged(float[] linearAcceleration,
                                                  long timeStamp) {
        // Get a local copy of the sensor values
        System.arraycopy(linearAcceleration, 0, this.linearAcceleration, 0,
                linearAcceleration.length);
    }

    @Override
    public void run() {
        handler.postDelayed(this, 25);

        plotData();
        logData();
    }

    /**
     * Create the plot colors.
     */
    private void initColor() {
        color = new PlotColor(this);

        plotAccelXAxisColor = color.getDarkBlue();
        plotAccelYAxisColor = color.getDarkGreen();
        plotAccelZAxisColor = color.getDarkRed();

        plotLinearAccelXAxisColor = color.getMidBlue();
        plotLinearAccelYAxisColor = color.getMidGreen();
        plotLinearAccelZAxisColor = color.getMidRed();
    }

    /**
     * Create the output graph line chart.
     */
    private void initPlot() {
        addPlot(plotAccelXAxisTitle, plotAccelXAxisKey, plotAccelXAxisColor);
        addPlot(plotAccelYAxisTitle, plotAccelYAxisKey, plotAccelYAxisColor);
        addPlot(plotAccelZAxisTitle, plotAccelZAxisKey, plotAccelZAxisColor);

        addPlot(plotLinearAccelXAxisTitle, plotLinearAccelXAxisKey,
                plotLinearAccelXAxisColor);
        addPlot(plotLinearAccelYAxisTitle, plotLinearAccelYAxisKey,
                plotLinearAccelYAxisColor);
        addPlot(plotLinearAccelZAxisTitle, plotLinearAccelZAxisKey,
                plotLinearAccelZAxisColor);
    }

    /**
     * Create the RMS Noise bar chart.
     */
    private void initGauges() {

    }

    /**
     * Add a plot to the graph.
     *
     * @param title The name of the plot.
     * @param key   The unique plot key
     * @param color The color of the plot
     */
    private void addPlot(String title, int key, int color) {
        dynamicPlot.addSeriesPlot(title, key, color);
    }

    private void showHelpDialog() {
        Dialog helpDialog = new Dialog(this);
        helpDialog.setCancelable(true);
        helpDialog.setCanceledOnTouchOutside(true);

        helpDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        helpDialog.setContentView(getLayoutInflater().inflate(R.layout.help,
                null));

        helpDialog.show();
    }

    /**
     * Begin logging data to an external .csv file.
     */
    private void startDataLog() {

        double K = 0;
        if (logData == false) {
            CharSequence text = "Logging Data";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(this, text, duration);
            toast.show();

            String headers = "Generation" + ",";

            headers += "Timestamp" + ",";

            headers += this.plotAccelXAxisTitle + ",";

            headers += this.plotAccelYAxisTitle + ",";

            headers += this.plotAccelZAxisTitle + ",";

            headers += this.plotLinearAccelXAxisTitle + ",";

            headers += this.plotLinearAccelYAxisTitle + ",";

            headers += this.plotLinearAccelZAxisTitle + ",";

            log = headers + "\n";

            iconLogger.setVisibility(View.VISIBLE);

            logData = true;
        }



        else {
            iconLogger.setVisibility(View.INVISIBLE);

            logData = false;
            writeLogToFile();

            for(int i = timeDiff.size()-1; i > 0; i--) {

                timeDiff.set(i, timeDiff.get(i) - timeDiff.get(i-1));
            }
            for(int i =0; i<accX.size();i++) {
                accXfilter.add(accX.get(i));
            }

            for(int i =0; i<accY.size();i++) {
                accYfilter.add(accY.get(i));
            }


            int n = accX.size();


            // % corrected state vectors
            double[] z = matrix(0f, n);
            // z =

            double[] x = matrix(0f, n);
            //% corrected error co-variance matrix

            double[] p = matrix(1, n); //one    P = ones(n,1);
            //% predicted state vectors

            double[] x_p = matrix(0f, n);//            x_p = zeros(n,1);
            //% predicted error co-variance matrix

            double[] p_p = matrix(0f, n);   // P_p = zeros(n,1);
            p_p[0] =2;
            //% variance of sensor noise
            //double r = 0.000625;

            Log.d("dd ", "AccX SIZE: " + ((accX.size())));
            Log.d("dd ", "AccX filter SIZE: " + ((accXfilter.size())));


            ArrayList<Double> sub1 = new ArrayList<Double>(accXfilter.subList(0,50));
            // ArrayList<Double> sub = new ArrayList<Double>(accXfilter.subList(j, j + 25));




            double sd1 = calculateSD(sub1);
            double r =sd1*sd1;
            Log.d("asdasdasdsadasdasdad", "standard DAVIATOIn" + sd1);
            double Q = 0.0005   ;
            for(int k =0; k <accXfilter.size()-1; k++){
                //% prediction
                x_p[k+1]= x[k];
                p_p[k+1] = p[k];

                //% correction
                K = p_p[k+1]/(p_p[k+1] + r);
                x[k+1] = x_p[k+1] + K*(accXfilter.get(k+1) - x_p[k+1]);
                p[k+1] = (1 - K)* p_p[k+1] + Q;


               if(accXfilter.get(k+1) != 0 )
                    accXfilter.set(k+1, x[k+1]);
                // Log.d(TAG, "Kalman: " + x[k+1] );
            }

         z = matrix(0f, n);
            // z =

             x = matrix(0f, n);
            //% corrected error co-variance matrix

           p = matrix(1, n); //one    P = ones(n,1);
            //% predicted state vectors

             x_p = matrix(0f, n);//            x_p = zeros(n,1);
            //% predicted error co-variance matrix

            p_p = matrix(0f, n);   // P_p = zeros(n,1);
            p_p[0] =2;
            //% variance of sensor noise
            //double r = 0.000625;

           ArrayList<Double> sub3 = new ArrayList<Double>(accYfilter.subList(0,50));
            double sdY = calculateSD(sub3);
            double rY =sd1*sd1;
            Log.d("asdasdasdsadasdasdad", "standard DAVIATOIn" + sdY);
            double QY = 0.0005   ;
            for(int k =0; k <accYfilter.size()-1; k++){
                //% prediction
                x_p[k+1]= x[k];
                p_p[k+1] = p[k];

                //% correction
                K = p_p[k+1]/(p_p[k+1] + rY);
                x[k+1] = x_p[k+1] + K*(accYfilter.get(k+1) - x_p[k+1]);
                p[k+1] = (1 - K)* p_p[k+1] + QY;


                if(accYfilter.get(k+1) != 0 )
                    accYfilter.set(k+1, x[k+1]);
                // Log.d(TAG, "Kalman: " + x[k+1] );
            }






            ArrayList<Double> sub2 = new ArrayList<Double>(accXfilter.subList(0,50));
            // ArrayList<Double> sub = new ArrayList<Double>(accXfilter.subList(j, j + 25));
            double sum1 = 0;
            for(int i = 0; i<sub2.size(); i++){
                sum1 += sub2.get(i);
            }

            double mean1 = (sum1/sub2.size());

            for(int i = 0; i < accXfilter.size(); i++){
                if(mean1 <0)
                accXfilter.set(i, accXfilter.get(i)-mean1);

                else
                    accXfilter.set(i, accXfilter.get(i)-mean1);

            }
            int j=0;
            double n1 = 0.05;
            double m = 0.1;
            // int k = 0;

            while( j < accXfilter.size()){
                if(j+10<accXfilter.size()) {
                    int temp = j;
                    ArrayList<Double> sub = new ArrayList<Double>(accXfilter.subList(j, j + 10));
                    double sd = calculateSD(sub);



                    double sum = 0.0;
                    // standardDeviation = 0.0;
                  //  double max = 0;
                    for(int i = 0; i<10; i++){
                        sum += sub.get(i);
                       // if(sub.get(i)>max){
                       //     max = sub.get(i);
                       // }
                    }

                    double mean = Math.abs(sum/sub.size());

                    if( sd < n1 && mean < m){
                        for(int i = j ; i<j+10; i++){
                            accXfilter.set(i, 0.0);


                        }

                        int a = j -1;
                        int pop = 0;

                        if(a>=0) {
                            while (accX.get(a) > 0 && a>0) {
                                accXfilter.set(a, 0.0);
                                a--;
                                pop++;
                            }


                        }



                        Log.d("askdakdjak ", "standard D " + sd + " Mean " + mean + " repeats: " + pop);

                    }




                }
                else{
                    ArrayList<Double> sub = new ArrayList<Double>(accXfilter.subList(j, accXfilter.size()));
                    double sd = calculateSD(sub);

                    double sum = 0.0;
                    // standardDeviation = 0.0;

                    for(int i = 0; i<sub.size(); i++){
                        sum += sub.get(i);
                    }

                    double mean = sum/sub.size();

                    if(sd >= n1){}
                    else if( sd < n1 && mean < m){
                        for(int i = j ; i<accXfilter.size(); i++){
                            accXfilter.set(i, 0.0);
                        }
                    }
                }


                j += 10;
            }

            int signChange = 0;
            for(int i = 50; i < accXfilter.size()-1; i++){
                if( signChange < 1 && accXfilter.get(i)*accXfilter.get(i+1)<0 && accXfilter.get(i+10)<0 ){
                    signChange++;
                }

                else if(accXfilter.get(i)*accXfilter.get(i+1)<0 && accXfilter.get(i-10)<0 && signChange < 2){
                    signChange++;
                }

                if(signChange >= 2){
                    accXfilter.set(i, 0.0);
                }


            }


//            ArrayList<Double> sub2Y = new ArrayList<Double>(accYfilter.subList(0,50));
//            // ArrayList<Double> sub = new ArrayList<Double>(accXfilter.subList(j, j + 25));
//            double sum1Y = 0;
//            for(int i = 0; i<sub2Y.size(); i++){
//                sum1Y += sub2Y.get(i);
//            }
//
//            double mean1Y = (sum1Y/sub2Y.size());
//
//            for(int i = 0; i < accYfilter.size(); i++){
//                if(mean1Y <0)
//                    accYfilter.set(i, accYfilter.get(i)-mean1Y);
//
//                else
//                    accYfilter.set(i, accYfilter.get(i)-mean1Y);
//
//            }
//            int jY=0;
//            double n1Y = 0.05;
//            double mY = 0.1;
//            // int kY = 0;
//
//            while( jY < accYfilter.size()){
//                if(jY+10<accYfilter.size()) {
//                    int tempY = jY;
//                    ArrayList<Double> subY = new ArrayList<Double>(accYfilter.subList(jY, jY + 10));
//                    double sd1Y = calculateSD(subY);
//
//
//
//                    double sumY = 0.0;
//                    // standardDeviation = 0.0;
//                    //  double max = 0;
//                    for(int i = 0; i<10; i++){
//                        sumY += subY.get(i);
//                        // if(sub.get(i)>max){
//                        //     max = sub.get(i);
//                        // }
//                    }
//
//                    double meanY = Math.abs(sumY/subY.size());
//
//                    if( sd1Y < sdY && meanY < mY){
//                        for(int i = jY ; i<jY+10; i++){
//                            accYfilter.set(i, 0.0);
//
//
//                        }
//
//                        int aY = jY -1;
//                        //int popY = 0;
//
//                        if(aY>=0) {
//                            while (accY.get(aY) < 0 && aY>
//                                    0) {
//                                accYfilter.set(aY, 0.0);
//                                aY--;
//                                //pop++;
//                            }
//
//
//                        }
//
//
//
//                        //Log.d("askdakdjak ", "standard D " + sd + " Mean " + mean + " repeats: " + pop);
//
//                    }
//
//
//
//
//                }
//                else{
//                    ArrayList<Double> subY = new ArrayList<Double>(accYfilter.subList(jY, accYfilter.size()));
//                    double sd1Y = calculateSD(subY);
//
//                    double sumY = 0.0;
//                    // standardDeviation = 0.0;
//
//                    for(int i = 0; i<subY.size(); i++){
//                        sumY += subY.get(i);
//                    }
//
//                    double meanY = sumY/subY.size();
//
//                    if(sd1Y >= n1Y){}
//                    else if( sdY < n1Y && meanY < mY){
//                        for(int i = jY ; i<accYfilter.size(); i++){
//                            accYfilter.set(i, 0.0);
//                        }
//                    }
//                }
//
//
//                jY += 10;
//            }

            calculation(accX);
           // calculation(accY);

            tv_result = (TextView) findViewById(R.id.tv_result);

            tv_result.setText("The distance is: " + String.format( "%.2f", (disXfilter.get(disXfilter.size()-1))*100) + " cm");




            double xAxis, yAxis, yAxisKalman, yAxisVelocity;

            xAxis = 0;
            GraphView graph = (GraphView) findViewById(R.id.graph);
            series = new LineGraphSeries<DataPoint>();
            seriesKalman = new LineGraphSeries<DataPoint>();
            seriesVelcoity = new LineGraphSeries<DataPoint>();

            for(int i = 0; i<accX.size()-2; i++){
                xAxis = xAxis + timeDiff.get(i+1);
                yAxis = accX.get(i);
                yAxisKalman = accXfilter.get(i);
                yAxisVelocity = velXfilter.get(i);
                series.appendData(new DataPoint(xAxis,yAxis), true,accX.size()-2 );
                seriesKalman.appendData(new DataPoint(xAxis,yAxisKalman), true,accXfilter.size()-2);
                 seriesVelcoity.appendData(new DataPoint (xAxis, yAxisVelocity), true, velXfilter.size()-2);

                series.setTitle("Time(s)");
                seriesKalman.setTitle("Kalman ");
                seriesVelcoity.setTitle( "Velocity");
                series.setColor(Color.GREEN);
                seriesKalman.setColor(Color.RED);
                seriesVelcoity.setColor(Color.WHITE);


                graph.addSeries(series);
                graph.addSeries(seriesKalman);
                graph.addSeries(seriesVelcoity);



                // enable scaling and scrolling

            }
            graph.getViewport().setScalable(true);
            graph.getViewport().setScalableY(true);


        }
    }

    /**
     * Plot the output data in the UI.
     */
    private void plotData() {
        dynamicPlot.setData(acceleration[0], plotAccelXAxisKey);
        dynamicPlot.setData(acceleration[1], plotAccelYAxisKey);
        dynamicPlot.setData(acceleration[2], plotAccelZAxisKey);

        dynamicPlot.setData(linearAcceleration[0], plotLinearAccelXAxisKey);
        dynamicPlot.setData(linearAcceleration[1], plotLinearAccelYAxisKey);
        dynamicPlot.setData(linearAcceleration[2], plotLinearAccelZAxisKey);

        dynamicPlot.draw();

        // Update the view with the new acceleration data
        xAxis.setText(df.format(linearAcceleration[0]));
        yAxis.setText(df.format(linearAcceleration[1]));
        zAxis.setText(df.format(linearAcceleration[2]));


    }

    /**
     * Log output data to an external .csv file.
     */
    int x =0;
    private float timestamp;
    private static final float NS2S = 1.0f / 1000000000.0f;
    //int x = 0;
    private void logData() {


        if (logData) {
            if (generation == 0) {
                logTime = System.currentTimeMillis();
            }

            double time = (double)(System.currentTimeMillis() - logTime)/1000;
            log += System.getProperty("line.separator");
            log += generation++ + ",";
            log += time + ",";

            log += acceleration[0] + ",";
            log += acceleration[1] + ",";
            log += acceleration[2] + ",";

            log += linearAcceleration[0] + ",";
            log += linearAcceleration[1] + ",";
            log += linearAcceleration[2] + ",";

            accX.add((double)linearAcceleration[0]);
            accY.add((double)linearAcceleration[2]);

            timeDiff.add((float)time);


            if(x == 50)
            {
                tv_result = (TextView) findViewById(R.id.tv_result);

                tv_result.setText("Move Now");

            }
            x++;
        }
    }

    /**
     * Write the logged data out to a persisted file.
     */
    private void writeLogToFile() {
        Calendar c = Calendar.getInstance();
        String filename = "FusedLinearAcceleration-" + c.get(Calendar.YEAR)
                + "-" + c.get(Calendar.DAY_OF_WEEK_IN_MONTH) + "-"
                + c.get(Calendar.HOUR) + "-" + c.get(Calendar.HOUR) + "-"
                + c.get(Calendar.MINUTE) + "-" + c.get(Calendar.SECOND)
                + ".csv";

        File dir = new File(Environment.getExternalStorageDirectory()
                + File.separator + "FusedLinearAcceleration" + File.separator
                + "Logs" + File.separator + "Acceleration");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(dir, filename);

        FileOutputStream fos;
        byte[] data = log.getBytes();
        try {
            fos = new FileOutputStream(file);
            fos.write(data);
            fos.flush();
            fos.close();

            CharSequence text = "Log Saved";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(this, text, duration);
            toast.show();
        } catch (FileNotFoundException e) {
            CharSequence text = e.toString();
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(this, text, duration);
            toast.show();
        } catch (IOException e) {
            // handle exception
        } finally {
            // Update the MediaStore so we can view the file without rebooting.
            // Note that it appears that the ACTION_MEDIA_MOUNTED approach is
            // now blocked for non-system apps on Android 4.4.
            MediaScannerConnection.scanFile(this, new String[]
                            {"file://" + Environment.getExternalStorageDirectory()}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(final String path,
                                                    final Uri uri) {
                            Log.i(tag, String.format(
                                    "Scanned path %s -> URI = %s", path));
                        }
                    });
        }
    }

    /**
     * Get the distance between fingers for the touch to zoom.
     *
     * @param event
     * @return
     */
    private final float fingerDist(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private static double[] matrix( double a, int n){
        double[] array = new double[n];
        for (int x = 0; x < array.length; x++) {

            array[x] = a;

        }
        return array;
    }


    public static double calculateSD(ArrayList<Double> x)
    {
        double sum = 0.0, standardDeviation = 0.0;

        for(int i = 0; i< x.size(); i++) {
            sum += x.get(i);
        }

        double mean = sum/x.size();

        for(int i = 0; i< x.size(); i++) {
            standardDeviation += Math.pow(x.get(i) - mean, 2);
        }

        return Math.sqrt(standardDeviation/x.size());
    }
    public void calculation (ArrayList<Double> x){


        velXfilter.add(0.0);
        //double max = 0;
        int maxi = 0;
        for(int i = 1; i < accX.size(); i++){

            if(accXfilter.get(i) != 0) {
                double val = velXfilter.get(i - 1) + (((accXfilter.get(i) + accXfilter.get(i-1))/2.0) * timeDiff.get(1));
                velXfilter.add(val);
            }

            else
                velXfilter.add(0.0);

//            double val = velXfilter.get(i - 1) + (((accXfilter.get(i))) * timeDiff.get(1));
//            velXfilter.add(val);

        }
        int count = 0;

        for(int i = 50; i <velXfilter.size()-1; i++){
            double val =  velXfilter.get(i);
            if(val > velXfilter.get(i+1) && val > velXfilter.get(i-1)) {
                maxi = maxi + i;
                count++;

                Log.d("tag", "MAXi " + i);

            }
        }

        if(count != 0)
        maxi = maxi/count;

        Log.d("tag", "MAXIIIII " + maxi);
        double nv = 0.0001;
        int jv = 0;

        while( jv < velXfilter.size()){

            if(jv+10<velXfilter.size()) {
                ArrayList<Double> sub = new ArrayList<Double>(velXfilter.subList(jv, jv + 10));
                double sd = calculateSD(sub);

//                double temp = 0;


                if(sd >= nv){}
                else if( sd < nv){
                    for(int i = jv ; i<jv+10; i++){
                        velXfilter.set(i, 0.0);
                    }
                }
            }


            else{
                ArrayList<Double> sub = new ArrayList<Double>(velXfilter.subList(jv, velXfilter.size()));
                double sd = calculateSD(sub);


                if(sd >= nv){}
                else if( sd < nv ){
                    for(int i = jv ; i<velXfilter.size(); i++){
                        velXfilter.set(i, 0.0);
                    }
                }
            }


            jv += 10;
        }

        double temp;
        disXfilter.add(0.0);
        for(int i = 1; i < velXfilter.size(); i++){
            temp = disXfilter.get(i-1);
            double val =  disXfilter.get(i-1)+(((velXfilter.get(i) + velXfilter.get(i-1))/2.0)*timeDiff.get(i-1));

            if(temp<val)
                disXfilter.add(val);

            else
                disXfilter.add(temp);

        }



        velYfilter.add(0.0);
        //double max = 0;
        int maxiY = 0;
        for(int i = 1; i < accY.size(); i++){

//            if(accXfilter.get(i) != 0) {
//                double val = velXfilter.get(i - 1) + (((accXfilter.get(i))) * timeDiff.get(1));
//                velXfilter.add(val);
//            }
//
//            else
//                velXfilter.add(0.0);

            double val = velYfilter.get(i - 1) + (((accYfilter.get(i))) * timeDiff.get(1));
            velYfilter.add(val);

        }
        int countY = 0;

        for(int i = 50; i <velYfilter.size()-1; i++){
            double val =  velYfilter.get(i);
            if(val > velYfilter.get(i+1) && val > velYfilter.get(i-1)) {
                maxiY = maxiY + i;
                countY++;

                Log.d("tag", "MAXiY " + i);

            }
        }

       // maxiY = maxiY/countY;

        Log.d("tag", "MAXIIIII " + maxiY);
        double nvY = 0.0001;
        int jvY = 0;

        while( jvY < velYfilter.size()){

            if(jvY+10<velYfilter.size()) {
                ArrayList<Double> subY = new ArrayList<Double>(velYfilter.subList(jvY, jvY + 10));
                double sdY = calculateSD(subY);

//                double tempY = 0;


                if(sdY >= nvY){}
                else if( sdY < nvY){
                    for(int i = jvY ; i<jvY+10; i++){
                        velYfilter.set(i, 0.0);
                    }
                }
            }


            else{
                ArrayList<Double> subY = new ArrayList<Double>(velYfilter.subList(jvY, velYfilter.size()));
                double sdY = calculateSD(subY);


                if(sdY >= nvY){}
                else if( sdY < nvY ){
                    for(int i = jvY ; i<velYfilter.size(); i++){
                        velYfilter.set(i, 0.0);
                    }
                }
            }


            jvY += 10;
        }

        double tempY;
        disYfilter.add(0.0);
        for(int i = 1; i < velYfilter.size(); i++){
            tempY = disYfilter.get(i-1);
            double valY =  disYfilter.get(i-1)+(((velYfilter.get(i)))*timeDiff.get(i-1));

            if(tempY>valY)
                disYfilter.add(valY);

            else
                disYfilter.add(tempY);

        }

        if(disYfilter.get(disYfilter.size()-1)>0.02){
            disYfilter.set(disYfilter.size()-1,disYfilter.get(disYfilter.size()-1));
        }

        else if(disYfilter.get(disYfilter.size()-1)< -0.02){
            disYfilter.set(disYfilter.size()-1,disYfilter.get(disYfilter.size()-1));
        }


        if(disXfilter.get(disXfilter.size()-1)>0.02){
            disXfilter.set(disXfilter.size()-1,disXfilter.get(disXfilter.size()-1));
        }

        else if(disXfilter.get(disXfilter.size()-1)< -0.02){
            disXfilter.set(disXfilter.size()-1,disXfilter.get(disXfilter.size()-1));
        }


        for(int mk = 2; mk < accX.size()-1; mk++) {
            Log.d("dd", "AccX: " + (accX.get(mk))  + "AccX Filtered: " + (accXfilter.get(mk)) + " vel " + (velXfilter.get(mk-1))+  " dis " + (disXfilter.get(mk-2)) + " index " + mk + " Time " + timeDiff.get(mk-1) );

        }
        for(int mk = 2; mk < accX.size()-1; mk++)
        Log.d("dd", "AccY: " + (accY.get(mk))  + "AccY Filtered: " + (accYfilter.get(mk)) + " vel " + (velYfilter.get(mk-1))+  " dis " + (disYfilter.get(mk-2)) + " index " + mk + " Time " + timeDiff.get(mk-1) );

        Log.d("dd ", "E_AccX: " + (accX.get(accX.size()-1)) + " E_VelX: " + (velXfilter.get(velXfilter.size()-1)) + " E_DisX: " + (disXfilter.get(disXfilter.size()-1)));

        Double xD =  disXfilter.get(disXfilter.size()-1);
        Double yD = disYfilter.get(disYfilter.size()-1);
        textView = (TextView) findViewById(R.id.textView);
        float answer = (float)((2*disXfilter.get(maxi-1) )) ;

       // Double answer = Math.sqrt((xD*xD)+(yD*yD));

       // Double answer = yD;
        textView.setText(String.format( "%.2f", (answer)*100)+ " cm");
       // tv_result.setText("The distance is: " + String.format( "%.2f", (answer) + " cm");


        //textView.setText("");

    }
}