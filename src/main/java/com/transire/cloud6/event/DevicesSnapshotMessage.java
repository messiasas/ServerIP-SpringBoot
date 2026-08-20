package com.transire.cloud6.event;

import com.transire.cloud6.device.Device;
import java.util.List;

public class DevicesSnapshotMessage {
    public String type = "devices_snapshot";
    public List<Device> devices;
}
