package org.epics.pvaccess.scope;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.epics.pvaccess.client.Lockable;
import org.epics.pvaccess.scope.SignalGenerator.Signal;
import org.epics.pvdata.misc.BitSet;
import org.epics.pvdata.property.PVTimeStamp;
import org.epics.pvdata.property.PVTimeStampFactory;
import org.epics.pvdata.property.TimeStamp;
import org.epics.pvdata.property.TimeStampFactory;
import org.epics.pvdata.pv.PVDoubleArray;
import org.epics.pvdata.pv.PVField;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;

public class ScopePvStructure implements Lockable {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public interface ScopePvStructureListener {
        public void scopeStructureChanged(BitSet changedBitSet);
    }

    private final Lock lock = new ReentrantLock();
    private final PVStructure pvStructure;
    private final ArrayList<ScopePvStructureListener> listeners = new ArrayList<ScopePvStructureListener>();

    private BitSet changedBitSet;
    private PVDoubleArray valueField;
    private PVTimeStamp timeStampField;
    private int timeStampFieldOffset;

    private final TimeStamp timeStamp = TimeStampFactory.create();
    
    private final Signal signal;

    public ScopePvStructure(PVStructure pvStructure)
    {
        this.pvStructure = pvStructure;
        changedBitSet = new BitSet(pvStructure.getNumberFields());
        valueField = (PVDoubleArray) getPVStructure().getScalarArrayField("value", ScalarType.pvDouble);
        timeStampField = PVTimeStampFactory.create();
        PVField ts = getPVStructure().getStructureField("timeStamp");
        timeStampField.attach(ts);
        timeStampFieldOffset = ts.getFieldOffset();

        signal = SignalGenerator.generateSawtoothWaveform(1.0, 100.0, 1000.0, 1.0);
        scheduler.scheduleAtFixedRate(this::process, 0, 1, TimeUnit.SECONDS);
    }

    public ScopePvStructure(PVStructure pvStructure, String signalType)
    {
        this.pvStructure = pvStructure;
        changedBitSet = new BitSet(pvStructure.getNumberFields());
        valueField = (PVDoubleArray) getPVStructure().getScalarArrayField("value", ScalarType.pvDouble);
        timeStampField = PVTimeStampFactory.create();
        PVField ts = getPVStructure().getStructureField("timeStamp");
        timeStampField.attach(ts);
        timeStampFieldOffset = ts.getFieldOffset();

        switch (signalType) {
        case "sawtooth":
            signal = SignalGenerator.generateSawtoothWaveform(1.0, 100.0, 1000.0, 0.1);
            break;
        case "gaussian":
            signal = SignalGenerator.generateGaussianWaveform(1.0, 100.0, 1000.0, 0.1);
            break;
        case "sine":
            signal = SignalGenerator.generateSineWaveform(1.0, 100.0, 1000.0, 0.1);
            break;
        case "square":
            signal = SignalGenerator.generateSquareWaveform(1.0, 100.0, 1000.0, 0.1);
            break;
        case "noise":
            signal = SignalGenerator.generateNoiseWaveform(1.0, 100.0, 1000.0, 0.1);
            break;
        default:
            signal = SignalGenerator.generateSawtoothWaveform(1.0, 100.0, 10.0, 0.1);
            break;
        }
        scheduler.scheduleAtFixedRate(this::process, 0, 100, TimeUnit.MILLISECONDS);
    }

    public PVStructure getPVStructure() {
        return pvStructure;
    }

    public void process() {
        changedBitSet.clear();
        final double[] ARRAY_VALUE = signal.nextListDouble(Instant.now());
        valueField.setCapacity(ARRAY_VALUE.length);
        valueField.setLength(ARRAY_VALUE.length);
        valueField.put(0, ARRAY_VALUE.length, ARRAY_VALUE, 0);
        
        changedBitSet.set(valueField.getFieldOffset());
        
        timeStamp.getCurrentTime();
        timeStampField.set(timeStamp);
        changedBitSet.set(timeStampFieldOffset);
        notifyListeners(changedBitSet);
    }

    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        lock.unlock();
    }

    public void registerListener(ScopePvStructureListener listener)
    {
        synchronized (listeners) {
            listeners.add((ScopePvStructureListener) listener);
        }
    }
    
    public void unregisterListener(ScopePvStructureListener listener)
    {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    public void notifyListeners(BitSet changedBitSet)
    {
        synchronized (listeners) {
            for (ScopePvStructureListener listener : listeners)
            {
                try {
                    listener.scopeStructureChanged(changedBitSet);
                }
                catch (Throwable th) {
                    Writer writer = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(writer);
                    th.printStackTrace(printWriter);
                    System.err.println("Unexpected exception caught: " + writer);
                }
            }
        }
    }
}
