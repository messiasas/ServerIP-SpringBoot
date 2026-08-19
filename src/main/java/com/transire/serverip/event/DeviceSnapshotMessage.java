package com.transire.serverip.event;

import java.util.List;
import com.transire.serverip.device.Device;

public class DeviceSnapshotMessage {
    public String type = "device_snapshot";
    public List<Device> devices;
}