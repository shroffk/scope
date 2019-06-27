# scope
A example pvaccess java server for emulating oscilloscope data

An example scope structure:  
```
sawtooth = structure 
    epics:nt/NTScalarArray:1.0[] signal 
        epics:nt/NTScalarArray:1.0 
            double[] value [0.0,1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0]
            string descriptor count
        epics:nt/NTScalarArray:1.0 
            double[] value [-1.0,-0.98,-0.96,-0.94,-0.92,-0.9,-0.88,-0.86,-0.84,-0.8200000000000001]
            string descriptor sawtooth
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
        long secondsPastEpoch 1561647684
        int nanoseconds 617000000
        int userTag 0
```
