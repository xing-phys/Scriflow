package org.eink.inkprobe.notebook;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.net.Inet4Address;
import java.net.InetAddress;

/** Select the actual Wi-Fi interface, including Wi-Fi without Internet access. */
final class NotebookCastNetwork {
    final Network network;
    final InetAddress address;
    private NotebookCastNetwork(Network network, InetAddress address) { this.network = network; this.address = address; }

    static NotebookCastNetwork find(Context context) {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) return null;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            LinkProperties properties = manager.getLinkProperties(network);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || properties == null) continue;
            for (LinkAddress link : properties.getLinkAddresses()) {
                InetAddress address = link.getAddress();
                if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                    return new NotebookCastNetwork(network, address);
            }
        }
        return null;
    }

    boolean matches(Context context) {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        LinkProperties properties = manager == null ? null : manager.getLinkProperties(network);
        NetworkCapabilities capabilities = manager == null ? null : manager.getNetworkCapabilities(network);
        if (properties == null || capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false;
        for (LinkAddress link : properties.getLinkAddresses()) if (address.equals(link.getAddress())) return true;
        return false;
    }
}
