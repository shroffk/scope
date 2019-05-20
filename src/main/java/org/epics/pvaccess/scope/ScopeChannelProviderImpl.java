package org.epics.pvaccess.scope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.epics.nt.NTScalarArray;
import org.epics.pvaccess.PVFactory;
import org.epics.pvaccess.client.AccessRights;
import org.epics.pvaccess.client.Channel;
import org.epics.pvaccess.client.ChannelArray;
import org.epics.pvaccess.client.ChannelArrayRequester;
import org.epics.pvaccess.client.ChannelFind;
import org.epics.pvaccess.client.ChannelFindRequester;
import org.epics.pvaccess.client.ChannelGet;
import org.epics.pvaccess.client.ChannelGetRequester;
import org.epics.pvaccess.client.ChannelListRequester;
import org.epics.pvaccess.client.ChannelProcess;
import org.epics.pvaccess.client.ChannelProcessRequester;
import org.epics.pvaccess.client.ChannelProvider;
import org.epics.pvaccess.client.ChannelPut;
import org.epics.pvaccess.client.ChannelPutGet;
import org.epics.pvaccess.client.ChannelPutGetRequester;
import org.epics.pvaccess.client.ChannelPutRequester;
import org.epics.pvaccess.client.ChannelRPC;
import org.epics.pvaccess.client.ChannelRPCRequester;
import org.epics.pvaccess.client.ChannelRequest;
import org.epics.pvaccess.client.ChannelRequester;
import org.epics.pvaccess.client.GetFieldRequester;
import org.epics.pvaccess.scope.ScopePvStructure.ScopePvStructureListener;
import org.epics.pvdata.misc.BitSet;
import org.epics.pvdata.monitor.Monitor;
import org.epics.pvdata.monitor.MonitorElement;
import org.epics.pvdata.monitor.MonitorRequester;
import org.epics.pvdata.pv.Field;
import org.epics.pvdata.pv.MessageType;
import org.epics.pvdata.pv.PVField;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.Status;
import org.epics.pvdata.pv.Status.StatusType;
import org.epics.pvdata.pv.StatusCreate;

public class ScopeChannelProviderImpl implements ChannelProvider {

    public static final String PROVIDER_NAME = "scope";
    private static final String[] HOSTED_CHANNELS = new String[] { "sawtooth", "gaussian", "sine", "square", "noise"};
    private static final Set<String> HOSTED_CHANNELS_SET = new HashSet<String>(Arrays.asList(HOSTED_CHANNELS));

    private static final StatusCreate statusCreate = PVFactory.getStatusCreate();
    private static final Status channelNotFoundStatus = statusCreate.createStatus(StatusType.ERROR, "channel not found", null);
    private static final Status okStatus = statusCreate.getStatusOK();
    private static final Status fieldDoesNotExistStatus = statusCreate.createStatus(StatusType.ERROR, "field does not exist", null);
    private static final Status destroyedStatus = statusCreate.createStatus(StatusType.ERROR, "channel destroyed", null);

    public ScopeChannelProviderImpl()
    {
        // not nice but users would like to see this
        System.out.println("Created 'scope' ChannelProvider that hosts the following channels: "
                + HOSTED_CHANNELS_SET.toString());
    }
    
    class ScopeChannelImpl implements Channel {
        private final String channelName;
        private final ChannelRequester channelRequester;
        private final ScopePvStructure scopePvStructure;

        private final ArrayList<ChannelRequest> channelRequests = new ArrayList<ChannelRequest>();

        public ScopeChannelImpl(String channelName, ChannelRequester channelRequester, ScopePvStructure pvTopStructure) {
            this.channelName = channelName;
            this.channelRequester = channelRequester;
            this.scopePvStructure = pvTopStructure;
            
            setConnectionState(ConnectionState.CONNECTED);
        }

        @Override
        public String getRequesterName() {
            return channelRequester.getRequesterName();
        }

        @Override
        public void message(String message, MessageType messageType) {
            System.err.println("[" + messageType + "] " + message);
        }

        @Override
        public ChannelProvider getProvider() {
            return ScopeChannelProviderImpl.this;
        }

        @Override
        public String getRemoteAddress() {
            return "local";
        }

        private volatile ConnectionState connectionState = ConnectionState.NEVER_CONNECTED;

        private void setConnectionState(ConnectionState state) {
            this.connectionState = state;
            channelRequester.channelStateChange(this, state);
        }

        @Override
        public ConnectionState getConnectionState() {
            return connectionState;
        }

        @Override
        public boolean isConnected() {
            return getConnectionState() == ConnectionState.CONNECTED;
        }


        private final AtomicBoolean destroyed = new AtomicBoolean(false);

        @Override
        public void destroy() {
            if (destroyed.getAndSet(true) == false) {
                destroyRequests();

                setConnectionState(ConnectionState.DISCONNECTED);
                setConnectionState(ConnectionState.DESTROYED);
            }
        }

        @Override
        public String getChannelName() {
            return this.channelName;
        }

        @Override
        public ChannelRequester getChannelRequester() {
            return channelRequester;
        }


        @Override
        public void getField(GetFieldRequester requester, String subField) {
            
            if (requester == null)
                throw new IllegalArgumentException("requester");
            
            if (destroyed.get())
            {
                requester.getDone(destroyedStatus, null);
                return;
            }
            
            Field field;
            if (subField == null || subField.isEmpty())
                field = scopePvStructure.getPVStructure().getField();
            else
                field = scopePvStructure.getPVStructure().getSubField(subField).getField();
            
            if (field != null)
                requester.getDone(okStatus, field);
            else
                requester.getDone(fieldDoesNotExistStatus, null);
            
        }

        @Override
        public AccessRights getAccessRights(PVField pvField) {
            return AccessRights.readWrite;
        }

        @Override
        public ChannelProcess createChannelProcess(ChannelProcessRequester channelProcessRequester, PVStructure pvRequest) {

            if (channelProcessRequester == null)
                throw new IllegalArgumentException("channelProcessRequester");

            if (destroyed.get()) {
                channelProcessRequester.channelProcessConnect(destroyedStatus, null);
                return null;
            }

            return new ScopeChannelProcessImpl(scopePvStructure, channelProcessRequester, pvRequest);
        }

        @Override
        public ChannelGet createChannelGet(ChannelGetRequester channelGetRequester, PVStructure pvRequest) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public ChannelPut createChannelPut(ChannelPutRequester channelPutRequester, PVStructure pvRequest) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public ChannelPutGet createChannelPutGet(ChannelPutGetRequester channelPutGetRequester, PVStructure pvRequest) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public ChannelRPC createChannelRPC(ChannelRPCRequester channelRPCRequester, PVStructure pvRequest) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Monitor createMonitor(MonitorRequester monitorRequester, PVStructure pvRequest) {

            if (monitorRequester == null)
                throw new IllegalArgumentException("monitorRequester");

            if (pvRequest == null)
                throw new IllegalArgumentException("pvRequest");

            if (destroyed.get()) {
                monitorRequester.monitorConnect(destroyedStatus, null, null);
                return null;
            }

            return new ScopeChannelMonitorImpl(scopePvStructure, monitorRequester, pvRequest);
        }

        @Override
        public ChannelArray createChannelArray(ChannelArrayRequester channelArrayRequester, PVStructure pvRequest) {
            // TODO Auto-generated method stub
            return null;
        }

        public void registerRequest(ChannelRequest request) {
            synchronized (channelRequests) {
                channelRequests.add(request);
            }
        }

        public void unregisterRequest(ChannelRequest request) {
            synchronized (channelRequests) {
                channelRequests.remove(request);
            }
        }

        private void destroyRequests() {
            synchronized (channelRequests) {
                while (!channelRequests.isEmpty())
                    channelRequests.get(channelRequests.size() - 1).destroy();
            }
        }

        class ScopeBasicChannelRequest implements ChannelRequest {
            protected final Channel channel;
            protected final ScopePvStructure scopePvStructure;
            protected final AtomicBoolean destroyed = new AtomicBoolean();
            protected final Mapper mapper;
            protected final ReentrantLock lock = new ReentrantLock();
            protected volatile boolean lastRequest = false;

            public ScopeBasicChannelRequest(Channel channel, ScopePvStructure scopePvStructure, PVStructure pvRequest) {
                this.channel = channel;
                this.scopePvStructure = scopePvStructure;
                if (pvRequest != null)
                    mapper = new Mapper(scopePvStructure.getPVStructure(), pvRequest);
                else
                    mapper = null;
                registerRequest(this);
            }

            @Override
            public void lock() {
                lock.lock();
            }

            @Override
            public void unlock() {
                lock.unlock();
            }

            @Override
            public final void destroy() {
                if (destroyed.getAndSet(true))
                    return;
                unregisterRequest(this);
                internalDestroy();
            }

            protected void internalDestroy() {
                // noop
            }

            @Override
            public void cancel() {
                // noop, not supported
            }

            @Override
            public Channel getChannel() {
                return channel;
            }

            @Override
            public void lastRequest() {
                lastRequest = true;
            }

        }

        class ScopeChannelProcessImpl extends ScopeBasicChannelRequest implements ChannelProcess {
            private final ChannelProcessRequester channelProcessRequester;

            public ScopeChannelProcessImpl(ScopePvStructure scopePvStructure,
                    ChannelProcessRequester channelProcessRequester, PVStructure pvRequest) {
                super(ScopeChannelImpl.this, scopePvStructure, pvRequest);

                this.channelProcessRequester = channelProcessRequester;

                channelProcessRequester.channelProcessConnect(okStatus, this);
            }

            /*
             * (non-Javadoc)
             * 
             * @see org.epics.pvaccess.client.ChannelProcess#process(boolean)
             */
            @Override
            public void process() {
                if (destroyed.get()) {
                    channelProcessRequester.processDone(destroyedStatus, this);
                    return;
                }

                scopePvStructure.lock();
                try {
                    scopePvStructure.process();
                } finally {
                    scopePvStructure.unlock();
                }

                channelProcessRequester.processDone(okStatus, this);

                if (lastRequest)
                    destroy();
            }
        }

        class ScopeChannelMonitorImpl extends ScopeBasicChannelRequest
                implements Monitor, ScopePvStructureListener, MonitorElement {
            private final MonitorRequester monitorRequester;
            private final PVStructure pvGetStructure;
            private final BitSet bitSet; // for user
            private final BitSet activeBitSet; // changed monitoring
            private final AtomicBoolean started = new AtomicBoolean(false);

            // TODO tmp
            private final BitSet allChanged;
            private final BitSet noOverrun;

            public ScopeChannelMonitorImpl(ScopePvStructure scopePvStructure,
                                           MonitorRequester monitorRequester,
                                           PVStructure pvRequest) {
                super(ScopeChannelImpl.this, scopePvStructure, pvRequest);

                this.monitorRequester = monitorRequester;

                pvGetStructure = mapper.getCopyStructure();
                activeBitSet = new BitSet(pvGetStructure.getNumberFields());
                activeBitSet.set(0); // initial get gets all

                bitSet = new BitSet(pvGetStructure.getNumberFields());

                allChanged = new BitSet(pvGetStructure.getNumberFields());
                allChanged.set(0);
                noOverrun = new BitSet(pvGetStructure.getNumberFields());

                monitorRequester.monitorConnect(okStatus, this, pvGetStructure.getStructure());
            }

            @Override
            public void internalDestroy() {
                scopePvStructure.unregisterListener(this);
            }

            @Override
            public void scopeStructureChanged(BitSet changedBitSet) {
                lock();
                activeBitSet.or(changedBitSet);

                // add to queue, trigger
                lock();
                scopePvStructure.lock();
                try {
                    mapper.updateCopyStructureOriginBitSet(activeBitSet, bitSet);
                    activeBitSet.clear();
                } finally {
                    scopePvStructure.unlock();
                    unlock();
                }
                unlock();
                // TODO not a safe copy...
                monitorRequester.monitorEvent(this);
            }

            @Override
            public Status start() {
                if (started.getAndSet(true))
                    return okStatus;

                // force monitor immediately
                scopeStructureChanged(allChanged);

                scopePvStructure.registerListener(this);

                return okStatus;
            }

            @Override
            public Status stop() {
                if (!started.getAndSet(false))
                    return okStatus;

                // TODO clear queue

                scopePvStructure.unregisterListener(this);

                return okStatus;
            }

            private final AtomicBoolean pooled = new AtomicBoolean(false);

            @Override
            public MonitorElement poll() {
                if (pooled.getAndSet(true))
                    return null;

                return this;
            }

            @Override
            public void release(MonitorElement monitorElement) {
                pooled.set(false);
            }

            @Override
            public PVStructure getPVStructure() {
                return pvGetStructure;
            }

            @Override
            public BitSet getChangedBitSet() {
                return allChanged;
            }

            @Override
            public BitSet getOverrunBitSet() {
                return noOverrun;
            }

        }

    }

    @Override
    public void destroy() {
        // TODO Auto-generated method stub

    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private ChannelFind channelFind = new ChannelFind() {

        @Override
        public ChannelProvider getChannelProvider() {
            return ScopeChannelProviderImpl.this;
        }

        @Override
        public void cancel() {
            // noop, sync call
        }
    };

    @Override
    public ChannelFind channelFind(String channelName, ChannelFindRequester channelFindRequester) {
        if (channelName == null)
            throw new IllegalArgumentException("channelName");

        if (channelFindRequester == null)
            throw new IllegalArgumentException("channelFindRequester");
        
        boolean found = isSupported(channelName);
        channelFindRequester.channelFindResult(
                okStatus,
                channelFind,
                found);
        
        return channelFind;
    }

    @Override
    public ChannelFind channelList(ChannelListRequester channelListRequester) {

        if (channelListRequester == null)
            throw new IllegalArgumentException("null requester");

        channelListRequester.channelListResult(okStatus, channelFind, HOSTED_CHANNELS_SET, true);
        return channelFind;
    }

    @Override
    public Channel createChannel(String channelName, ChannelRequester channelRequester, short priority) {

        if (channelName == null)
            throw new IllegalArgumentException("channelName");

        if (channelRequester == null)
            throw new IllegalArgumentException("channelRequester");

        if (priority < ChannelProvider.PRIORITY_MIN || priority > ChannelProvider.PRIORITY_MAX)
            throw new IllegalArgumentException("priority out of range");

        Channel channel = isSupported(channelName)
                ? new ScopeChannelImpl(channelName, channelRequester, getTopStructure(channelName))
                : null;

        Status status = (channel == null) ? channelNotFoundStatus : okStatus;
        channelRequester.channelCreated(status, channel);

        return channel;
    }

    private synchronized ScopePvStructure getTopStructure(String channelName) {
        return new ScopePvStructure(channelName);
    }

    @Override
    public Channel createChannel(String channelName, ChannelRequester channelRequester, short priority, String address) {
        throw new UnsupportedOperationException();
    }


    private boolean isSupported(String channelName) {
        return HOSTED_CHANNELS_SET.contains(channelName) || channelName.startsWith(PROVIDER_NAME);
    }
}
