package com.fitsupplepos.model;

import jakarta.persistence.*;

/** Generic key/value application settings not covered by a dedicated settings entity. */
@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", length = 2000)
    private String value;

    public AppSetting() {}
    public AppSetting(String key, String value) { this.key = key; this.value = value; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
