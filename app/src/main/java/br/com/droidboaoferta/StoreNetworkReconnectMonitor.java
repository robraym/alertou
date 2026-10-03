package br.com.droidboaoferta;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;

/** Tracks validated internet access and coalesces one store retry when it returns. */
final class StoreNetworkReconnectMonitor {
    static final String ACTION_CHANGED =
            BuildConfig.APPLICATION_ID + ".action.STORE_NETWORK_CHANGED";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Context appContext;
    private Runnable reconnectAction;
    private final Set<Network> validatedNetworks = new HashSet<>();
    private boolean online;
    private boolean started;
    private long reconnectToken;

    void start(Context context, Runnable reconnectAction) {
        if (started) return;
        appContext = context.getApplicationContext();
        this.reconnectAction = reconnectAction;
        connectivityManager = (ConnectivityManager) appContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        online = isOnline(appContext);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                updateNetwork(network, capabilities != null
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
            }

            @Override
            public void onLost(Network network) {
                updateNetwork(network, false);
            }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
        started = true;
        notifyChanged();
    }

    void stop() {
        reconnectToken++;
        handler.removeCallbacksAndMessages(null);
        if (started && connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (RuntimeException ignored) {
            }
        }
        started = false;
        networkCallback = null;
        reconnectAction = null;
        validatedNetworks.clear();
    }

    private synchronized void updateNetwork(Network network, boolean validated) {
        if (validated) validatedNetworks.add(network);
        else validatedNetworks.remove(network);
        update(!validatedNetworks.isEmpty());
    }

    private synchronized void update(boolean nowOnline) {
        if (online == nowOnline) return;
        boolean wasOnline = online;
        online = nowOnline;
        notifyChanged();
        if (!wasOnline && nowOnline && reconnectAction != null) {
            long token = ++reconnectToken;
            handler.postDelayed(() -> {
                synchronized (StoreNetworkReconnectMonitor.this) {
                    if (token != reconnectToken || !online || reconnectAction == null) return;
                    reconnectAction.run();
                }
            }, 1_200L);
        }
    }

    private void notifyChanged() {
        if (appContext != null) {
            appContext.sendBroadcast(new Intent(ACTION_CHANGED)
                    .setPackage(appContext.getPackageName()));
        }
    }

    static boolean isOnline(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
