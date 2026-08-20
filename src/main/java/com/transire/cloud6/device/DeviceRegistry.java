package com.transire.cloud6.device;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceRegistry {
    private final Map<String, Device> devices = new ConcurrentHashMap<>();

    public Device get(String name) { return devices.get(name); }
    public void put(Device device) { devices.put(device.getName(), device); }
    public List<Device> snapshot() { return new ArrayList<>(devices.values()); }
}
