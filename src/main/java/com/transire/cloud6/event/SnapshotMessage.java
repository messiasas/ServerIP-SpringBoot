package com.transire.cloud6.event;

import java.util.List;

public class SnapshotMessage {
    public String type = "snapshot";
    public int totalConnections;
    public int activeConnections;
    public List<TrafficEvent> history;
}
