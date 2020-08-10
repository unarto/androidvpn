package net.ivpn.client.v2.network;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableField;
import androidx.databinding.ObservableList;

import net.ivpn.client.common.prefs.NetworkProtectionPreference;
import net.ivpn.client.common.prefs.ServerType;
import net.ivpn.client.common.prefs.Settings;
import net.ivpn.client.ui.network.NetworkNavigator;
import net.ivpn.client.ui.network.OnNetworkFeatureStateChanged;
import net.ivpn.client.ui.network.OnNetworkSourceChangedListener;
import net.ivpn.client.vpn.local.NetworkController;
import net.ivpn.client.vpn.model.NetworkSource;
import net.ivpn.client.vpn.model.NetworkState;
import net.ivpn.client.vpn.model.WifiItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import static net.ivpn.client.vpn.model.NetworkSource.WIFI;

public class NetworkViewModel implements OnNetworkSourceChangedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkViewModel.class);

    public final ObservableBoolean isNetworkFeatureEnabled = new ObservableBoolean();
    public final ObservableField<NetworkState> defaultState = new ObservableField<>();
    public final ObservableField<NetworkState> mobileDataState = new ObservableField<>();
    public final ObservableList<WifiItem> wifiItemList = new ObservableArrayList<>();
    public final ObservableField<String> networkTitle = new ObservableField<>();
    public final ObservableField<NetworkState> networkState = new ObservableField<>();
    public OnNetworkFeatureStateChanged onNetworkFeatureStateChanged = new OnNetworkFeatureStateChanged() {
        @Override
        public void onNetworkFeatureStateChanged(boolean isEnabled) {
            handleNetworkFeatureState(isEnabled);
        }

        @Override
        public void toRules() {
            navigator.toRules();
        }
    };

    private WifiManager wifiManager;
    private NetworkNavigator navigator;
    private Settings settings;
    private NetworkController networkController;
    private NetworkProtectionPreference networkProtectionPreference;

    @Inject
    NetworkViewModel(Context context, NetworkProtectionPreference networkProtectionPreference,
                     Settings settings, NetworkController networkController) {
        this.settings = settings;
        this.networkProtectionPreference = networkProtectionPreference;
        this.networkController = networkController;
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        init();
    }

    public void onStart() {
        Log.d("NetworkController", "onStart: add network listener to NetworkViewModel ");
        networkController.setNetworkSourceChangedListener(this);
    }

    public void onStop() {
        Log.d("NetworkController", "onStop: Remove network listener from NetworkViewModel ");
        networkController.removeNetworkSourceListener();
    }

    public void setNavigator(NetworkNavigator navigator) {
        this.navigator = navigator;
    }

    public void reset() {

    }

    private void init() {
        isNetworkFeatureEnabled.set(settings.isNetworkRulesEnabled());
        defaultState.set(networkProtectionPreference.getDefaultNetworkState());
        mobileDataState.set(networkProtectionPreference.getMobileDataNetworkState());
        updateNetworksRules();
    }

    private List<WifiConfiguration> scan() {
        List<WifiConfiguration> wifiItems = wifiManager.getConfiguredNetworks();
        return wifiItems == null ? new ArrayList<>() : wifiItems;
    }

    private void updateNetworksRules() {
        List<WifiItem> wifiItems = new ArrayList<>();
        Set<String> trustedWifiList = getWifiListMarkedAsTrusted();
        Set<String> untrustedWifiList = getWifiListMarkedAsUntrusted();
        Set<String> noneWifiList = getWifiListMarkedAsNone();
        WifiItem item;

        for (WifiConfiguration configuration : scan()) {
            if (configuration.SSID == null) {
                continue;
            }
            item = new WifiItem(configuration.SSID, NetworkState.DEFAULT);
            if (trustedWifiList.contains(configuration.SSID)) {
                item.setNetworkState(NetworkState.TRUSTED);
            } else if (untrustedWifiList.contains(configuration.SSID)) {
                item.setNetworkState(NetworkState.UNTRUSTED);
            } else if (noneWifiList.contains(configuration.SSID)) {
                item.setNetworkState(NetworkState.NONE);
            }
            wifiItems.add(item);
        }

        Collections.sort(wifiItems, (item1, item2) -> item1.getTitle().compareToIgnoreCase(item2.getTitle()));

        wifiItemList.clear();
        wifiItemList.addAll(wifiItems);
    }

    private void handleNetworkFeatureState(boolean isEnabled) {
        isNetworkFeatureEnabled.set(isEnabled);
        if (isEnabled) {
            if (!navigator.shouldAskForLocationPermission()) {
                applyNetworkFeatureState(true);
            }
        } else {
            applyNetworkFeatureState(false);
        }
    }

    public void applyNetworkFeatureState(boolean isEnabled) {
        LOGGER.info("applyNetworkFeatureState: isEnabled = " + isEnabled);
        isNetworkFeatureEnabled.set(isEnabled);
        settings.putSettingsNetworkRules(isEnabled);
        if (isEnabled) {
            networkController.enableWifiWatcher();
        } else {
            networkController.disableWifiWatcher();
        }
    }

    private Set<String> getWifiListMarkedAsTrusted() {
        return networkProtectionPreference.getTrustedWifiList();
    }

    private Set<String> getWifiListMarkedAsUntrusted() {
        return networkProtectionPreference.getUntrustedWifiList();
    }

    private Set<String> getWifiListMarkedAsNone() {
        return networkProtectionPreference.getNoneWifiList();
    }

    @Override
    public void onNetworkSourceChanged(NetworkSource source) {
        Log.d("NetworkController", "onNetworkSourceChanged: source = " + source);
        if (source == null) {
            return;
        }
        if (source.equals(WIFI)) {
            Log.d("NetworkController", "onNetworkSourceChanged: ssid = " + source.getSsid());
        }

        networkState.set(source.getFinalState());
        networkTitle.set(source.getTitle());
    }

    @Override
    public void onDefaultNetworkStateChanged(NetworkState defaultState) {
    }
}