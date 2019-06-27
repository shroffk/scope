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
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.epics.nt.NTScalarArray;
import org.epics.pvaccess.client.Lockable;
import org.epics.pvaccess.scope.SignalGenerator.Signal;
import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.factory.PVDataFactory;
import org.epics.pvdata.factory.StandardFieldFactory;
import org.epics.pvdata.misc.BitSet;
import org.epics.pvdata.property.PVTimeStamp;
import org.epics.pvdata.property.PVTimeStampFactory;
import org.epics.pvdata.property.TimeStamp;
import org.epics.pvdata.property.TimeStampFactory;
import org.epics.pvdata.pv.Field;
import org.epics.pvdata.pv.PVDoubleArray;
import org.epics.pvdata.pv.PVField;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.PVStructureArray;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.Structure;
import org.epics.pvdata.pv.StructureArrayData;

public class ScopePvStructure implements Lockable {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public interface ScopePvStructureListener {
        public void scopeStructureChanged(BitSet changedBitSet);
    }

    private final Lock lock = new ReentrantLock();
    private final ArrayList<ScopePvStructureListener> listeners = new ArrayList<ScopePvStructureListener>();


    // Structure describing a axis
    static final String SCOPE_AXIS_DIR = "dir";
    static final String SCOPE_AXIS_SIDE = "side";
    static final String SCOPE_AXIS_LABEL = "label";

    static final Structure SCOPE_AXIS = FieldFactory.getFieldCreate().createStructure(
            new String[] { SCOPE_AXIS_DIR, SCOPE_AXIS_SIDE, SCOPE_AXIS_LABEL },
            new Field[] {
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString),
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString),
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString) });

    // Structure describing a trace
    static final String SCOPE_TRACE_X = "x";
    static final String SCOPE_TRACE_Y = "y";
    static final String SCOPE_TRACE_XAXIS = "xaxis";
    static final String SCOPE_TRACE_YAXIS = "yaxis";
    static final String SCOPE_TRACE_LABEL = "label";
    static final String SCOPE_TRACE_XERR = "xerr";
    static final String SCOPE_TRACE_YERR ="yerr";
    static final String SCOPE_TRACE_COLOR = "color";
    static final String SCOPE_TRACE_MARKER = "marker";
    
    static final Structure SCOPE_TRACE = FieldFactory.getFieldCreate().createStructure(
            new String[] { SCOPE_TRACE_X,
                           SCOPE_TRACE_Y,
                           SCOPE_TRACE_XAXIS,
                           SCOPE_TRACE_YAXIS,
                           SCOPE_TRACE_LABEL,
                           SCOPE_TRACE_XERR,
                           SCOPE_TRACE_YERR,
                           SCOPE_TRACE_COLOR,
                           SCOPE_TRACE_MARKER},
            new Field[] {
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString),
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString),
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString),
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString),
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString),
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString),
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString),
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString),
                    FieldFactory.getFieldCreate().createScalar(ScalarType.pvString), });

    // Complete Scope data structure
    static final Structure SCOPE = FieldFactory.getFieldCreate().createStructure(
            new String[] { "signal", "axis", "trace", "timeStamp"},
            new Field[] {
                    FieldFactory.getFieldCreate().createStructureArray(NTScalarArray.createBuilder().value(ScalarType.pvDouble).addDescriptor().createStructure()),
                    FieldFactory.getFieldCreate().createStructureArray(SCOPE_AXIS),
                    FieldFactory.getFieldCreate().createStructureArray(SCOPE_TRACE),
                    FieldFactory.getFieldCreate().createStructure(StandardFieldFactory.getStandardField().timeStamp())
            });

    private final TimeStamp timeStamp = TimeStampFactory.create();
    
    private final Signal signal;

    private final PVStructure pvStructure;
    private BitSet changedBitSet;
    private NTScalarArray valueField;
    private PVTimeStamp timeStampField;
    private int timeStampFieldOffset;
    private int valueFieldOffset;

    public ScopePvStructure(String signalType)
    {
        this.pvStructure = PVDataFactory.getPVDataCreate().createPVStructure(SCOPE);

        int elementCount = 10;
       
        // Initialize the basic structure.
        initialize(this.pvStructure, signalType, elementCount);

        changedBitSet = new BitSet(this.pvStructure.getNumberFields());

        StructureArrayData data = new StructureArrayData();
        this.pvStructure.getStructureArrayField("signal").get(0, 2, data);
        valueFieldOffset = this.pvStructure.getStructureArrayField("signal").getFieldOffset();
        valueField = NTScalarArray.wrap(data.data[1]);

        timeStampField = PVTimeStampFactory.create();
        PVField ts = this.pvStructure.getStructureField("timeStamp");
        timeStampField.attach(ts);
        timeStampFieldOffset = ts.getFieldOffset();

        switch (signalType) {
        case "sawtooth":
            signal = SignalGenerator.generateSawtoothWaveform(1.0, 100.0, (double) elementCount, 0.1);
            break;
        case "gaussian":
            signal = SignalGenerator.generateGaussianWaveform(1.0, 100.0, (double) elementCount, 0.1);
            break;
        case "sine":
            signal = SignalGenerator.generateSineWaveform(1.0, 100.0, (double) elementCount, 0.1);
            break;
        case "square":
            signal = SignalGenerator.generateSquareWaveform(1.0, 100.0, (double) elementCount, 0.1);
            break;
        case "noise":
            signal = SignalGenerator.generateNoiseWaveform(1.0, 100.0, (double) elementCount, 0.1);
            break;
        default:
            signal = SignalGenerator.generateSawtoothWaveform(1.0, 100.0, (double) elementCount, 0.1);
            break;
        }
        scheduler.scheduleAtFixedRate(this::process, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void initialize(PVStructure pvStructure, String signalType, int elementCount) {

        NTScalarArray ntValue1 = NTScalarArray.createBuilder().value(ScalarType.pvDouble).addDescriptor().create();
        ntValue1.getDescriptor().put("count");
        PVDoubleArray valueField = (PVDoubleArray) ntValue1.getValue();
        IntStream.rangeClosed(0, (int) elementCount).toArray();
        double[] range = DoubleStream.iterate(0, n -> n + 1).limit(elementCount).toArray();
        valueField.put(0, elementCount, range, 0);

        NTScalarArray value2 = NTScalarArray.createBuilder().value(ScalarType.pvDouble).addDescriptor().create();
        value2.getDescriptor().put(signalType);

        PVStructureArray values = pvStructure.getStructureArrayField("signal");
        values.put(0, 2, new PVStructure[] {ntValue1.getPVStructure(), value2.getPVStructure()}, 0);

        PVStructure axis1 = PVDataFactory.getPVDataCreate().createPVStructure(SCOPE_AXIS);
        axis1.getStringField(SCOPE_AXIS_DIR).put("x");
        axis1.getStringField(SCOPE_AXIS_LABEL).put("T/D");
        PVStructure axis2 = PVDataFactory.getPVDataCreate().createPVStructure(SCOPE_AXIS);
        axis2.getStringField(SCOPE_AXIS_DIR).put("y");
        axis2.getStringField(SCOPE_AXIS_LABEL).put("Voltage");

        PVStructureArray axes = pvStructure.getStructureArrayField("axis");
        axes.put(0, 2, new PVStructure[] {axis1, axis2} , 0);

        PVStructure trace1 = PVDataFactory.getPVDataCreate().createPVStructure(SCOPE_TRACE);
        trace1.getStringField(SCOPE_TRACE_X).put("count");
        trace1.getStringField(SCOPE_TRACE_Y).put(signalType);
        trace1.getStringField(SCOPE_TRACE_XAXIS).put("x");
        trace1.getStringField(SCOPE_TRACE_YAXIS).put("y");

        PVStructureArray traces = pvStructure.getStructureArrayField("trace");
        traces.put(0, 1, new PVStructure[] {trace1}, 0);
    }

    public void process() {
        changedBitSet.clear();

        final double[] ARRAY_VALUE = signal.nextListDouble(Instant.now());
        
        valueField.getValue().setCapacity(ARRAY_VALUE.length);
        valueField.getValue().setLength(ARRAY_VALUE.length);
        ((PVDoubleArray)valueField.getValue()).put(0, ARRAY_VALUE.length, ARRAY_VALUE, 0);

        changedBitSet.set(valueFieldOffset);

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

    public PVStructure getPVStructure() {
        return this.pvStructure;
    }
}
