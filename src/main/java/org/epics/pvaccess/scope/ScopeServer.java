package org.epics.pvaccess.scope;

import org.epics.pvaccess.server.impl.remote.ServerContextImpl;
import org.epics.pvaccess.server.impl.remote.plugins.DefaultBeaconServerDataProvider;

public class ScopeServer {

    public static void main(String[] args) {
        // Create a context with default configuration values.
        final ServerContextImpl context = new ServerContextImpl();
        context.setBeaconServerStatusProvider(new DefaultBeaconServerDataProvider(context));
        
        try {
            context.initialize(new ScopeChannelProviderImpl());
        } catch (Throwable th) {
            th.printStackTrace();
        }

        // Display basic information about the context.
        System.out.println(context.getVersion().getVersionString());
        context.printInfo(); System.out.println();
        context.getChannelProviders().stream().forEach(p->{
            System.out.println(p.getProviderName());
        });

        new Thread(new Runnable() {
            
            @Override
            public void run() {
                try {
                    System.out.println("Running server...");
                    context.run(0);
                    System.out.println("Done.");
                } catch (Throwable th) {
                    System.out.println("Failure:");
                    th.printStackTrace();
                }
            }
        }, "pvAccess server").start();
    }
}
