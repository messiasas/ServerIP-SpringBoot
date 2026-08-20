package com.transire.cloud6.device;

public class Device {
    private String name;
    private String ip;
    private int replyPort;
    private String family;
    private String registeredAt;
    private String lastSeen;
    private String lastPingBackAt;
    private String lastPingBackStatus = "pendente";
    private String lastPingBackMessage = "";

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getIp() { return ip; }
    public void setIp(String v) { this.ip = v; }
    public int getReplyPort() { return replyPort; }
    public void setReplyPort(int v) { this.replyPort = v; }
    public String getFamily() { return family; }
    public void setFamily(String v) { this.family = v; }
    public String getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(String v) { this.registeredAt = v; }
    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String v) { this.lastSeen = v; }
    public String getLastPingBackAt() { return lastPingBackAt; }
    public void setLastPingBackAt(String v) { this.lastPingBackAt = v; }
    public String getLastPingBackStatus() { return lastPingBackStatus; }
    public void setLastPingBackStatus(String v) { this.lastPingBackStatus = v; }
    public String getLastPingBackMessage() { return lastPingBackMessage; }
    public void setLastPingBackMessage(String v) { this.lastPingBackMessage = v; }
}
