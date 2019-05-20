package org.epics.pvaccess.scope;
import java.util.concurrent.TimeUnit;
import org.epics.pvaccess.ClientFactory;
import org.epics.pvaccess.client.Channel;
import org.epics.pvaccess.client.Channel.ConnectionState;
import org.epics.pvaccess.client.ChannelProvider;
import org.epics.pvaccess.client.ChannelProviderRegistry;
import org.epics.pvaccess.client.ChannelProviderRegistryFactory;
import org.epics.pvaccess.client.ChannelRequester;
import org.epics.pvdata.copy.CreateRequest;
import org.epics.pvdata.monitor.Monitor;
import org.epics.pvdata.monitor.MonitorElement;
import org.epics.pvdata.monitor.MonitorRequester;
import org.epics.pvdata.pv.MessageType;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.Status;
import org.epics.pvdata.pv.Structure;

class Demo implements ChannelRequester, MonitorRequester
{
    final private static CreateRequest request_creater = CreateRequest.create();
    private static ChannelProvider provider;
    final private PVStructure read_request = request_creater.createRequest("field()");
    final private Channel channel;
    private Monitor value_monitor = null;

    Demo(final String name) throws Exception
    {
        channel = provider.createChannel(name, this, ChannelProvider.PRIORITY_DEFAULT);
    }

    // ChannelRequester
    @Override
    public String getRequesterName()
    {
        return getClass().getName();
    }

    // ChannelRequester
    @Override
    public void message(final String message, final MessageType type)
    {
        System.out.println("Message " + type + ": " + message);
    }

    // ChannelRequester
    @Override
    public void channelCreated(final Status status, final Channel channel)
    {
        if (status.isSuccess())
            System.out.println("Channel " + channel.getChannelName() + " created");
        else
            System.out.println("Channel " + channel.getChannelName() + " create problem: " + status.getMessage());
    }

    // ChannelRequester
    @Override
    public void channelStateChange(final Channel channel, final ConnectionState state)
    {
        System.out.println("Channel " + channel.getChannelName() + " state: " + state);
        switch (state)
        {
        case CONNECTED:
            subscribe();
            break;
        case DISCONNECTED:
        default:
            // Ignore
        }
    }

    private void subscribe()
    {
        synchronized (this)
        {   // Avoid double-subscription
            if (this.value_monitor != null)
                return;
            value_monitor = channel.createMonitor(this, read_request);
        }
    }

    // MonitorRequester
    @Override
    public void monitorConnect(final Status status, final Monitor monitor,
            final Structure structure)
    {
        if (status.isSuccess())
            monitor.start();
    }

    // MonitorRequester
    @Override
    public void monitorEvent(final Monitor monitor)
    {
        MonitorElement update;
        while ((update = monitor.poll()) != null)
        {
            try
            {
                String value = update.getPVStructure().toString().replace("\n", "");
                if (value.length() > 1000)
                    value = value.substring(0, 1000) + "...";
                System.out.println(channel.getChannelName() + " = " + value);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
            monitor.release(update);
        }
    }

    // MonitorRequester
    @Override
    public void unlisten(final Monitor monitor)
    {
        // Ignore
    }

    public static void main(String[] args) throws Exception
    {
//        System.setProperty("EPICS_PVA_DEBUG", "3");
        ClientFactory.start();
        final ChannelProviderRegistry registry = ChannelProviderRegistryFactory.getChannelProviderRegistry();
        provider = registry.getProvider("pva");

//        new Demo("counter");
        new Demo("sawtooth");
////        TimeUnit.SECONDS.sleep(5);
        new Demo("gaussian");
////        TimeUnit.SECONDS.sleep(3);
//        new Demo("simpleCounter");
        TimeUnit.SECONDS.sleep(300);
        System.exit(0);
    }
}