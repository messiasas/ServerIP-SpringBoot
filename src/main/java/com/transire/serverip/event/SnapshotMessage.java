package com.transire.serverip.event;

import java.util.List;

public class SnapshotMessage{

    public String type = "snapshot";
    public int totConnections;
    public int activeConnections;
    public List<EventsTraffic> history;

}