# scope
A example pvaccess java server for emulating oscilloscope data

An example scope structure:  
```
sawtooth = structure 
    structure[] signal 
        structure 
            string id count
            double[] value [0.0,1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0]
        structure 
            string id sawtooth
            double[] value [0.20199999999999996,0.22199999999999998,0.242,0.262,0.28200000000000003,0.3019999999999998,0.32200000000000006,0.34199999999999986,0.3620000000000001,0.3819999999999999]
    structure[] axis 
        structure 
            string dir x
            string side 
            string label T/D
        structure 
            string dir y
            string side 
            string label Voltage
    structure[] trace 
        structure 
            string x count
            string y sawtooth
            string xaxis x
            string yaxis y
            string label 
            string xerr 
            string yerr 
            string color 
            string marker 
    time_t timeStamp
        long secondsPastEpoch 1558364965
        int nanoseconds 663000000
```