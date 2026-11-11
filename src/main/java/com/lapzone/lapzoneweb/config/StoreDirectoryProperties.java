package com.lapzone.lapzoneweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "lapzone.stores")
public class StoreDirectoryProperties {

    private List<Branch> branches = new ArrayList<>();

    public List<Branch> getBranches() {
        return branches;
    }

    public void setBranches(List<Branch> branches) {
        this.branches = branches == null ? new ArrayList<>() : branches;
    }

    public static class Branch {
        private String name;
        private String address;
        private String phone;
        private String openingHours;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getOpeningHours() { return openingHours; }
        public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

        public String getMapUrl() {
            if (address == null || address.isBlank()) {
                return null;
            }
            return "https://www.google.com/maps/search/?api=1&query="
                    + URLEncoder.encode(address.trim(), StandardCharsets.UTF_8);
        }

        public boolean hasInformation() {
            return hasText(name) || hasText(address) || hasText(phone) || hasText(openingHours);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
