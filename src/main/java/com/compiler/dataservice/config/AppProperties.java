package com.compiler.dataservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Security security = new Security();
    private Queue queue = new Queue();
    private Execution execution = new Execution();
    private RateLimit rateLimit = new RateLimit();

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Queue getQueue() { return queue; }
    public void setQueue(Queue queue) { this.queue = queue; }

    public Execution getExecution() { return execution; }
    public void setExecution(Execution execution) { this.execution = execution; }

    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

    public static class Security {
        private List<String> apiKeys = new ArrayList<>();

        public List<String> getApiKeys() { return apiKeys; }
        public void setApiKeys(List<String> apiKeys) { this.apiKeys = apiKeys; }
    }

    public static class Queue {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int capacity = 20;

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
    }

    public static class Execution {
        private int timeoutSeconds = 10;
        private int gracePeriodSeconds = 3;
        private int totalTimeoutSeconds = 25;
        private String memoryLimit = "256m";
        private String cpuLimit = "0.5";
        private int maxOutputBytes = 65536;

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getGracePeriodSeconds() { return gracePeriodSeconds; }
        public void setGracePeriodSeconds(int gracePeriodSeconds) { this.gracePeriodSeconds = gracePeriodSeconds; }

        public int getTotalTimeoutSeconds() { return totalTimeoutSeconds; }
        public void setTotalTimeoutSeconds(int totalTimeoutSeconds) { this.totalTimeoutSeconds = totalTimeoutSeconds; }

        public String getMemoryLimit() { return memoryLimit; }
        public void setMemoryLimit(String memoryLimit) { this.memoryLimit = memoryLimit; }

        public String getCpuLimit() { return cpuLimit; }
        public void setCpuLimit(String cpuLimit) { this.cpuLimit = cpuLimit; }

        public int getMaxOutputBytes() { return maxOutputBytes; }
        public void setMaxOutputBytes(int maxOutputBytes) { this.maxOutputBytes = maxOutputBytes; }
    }

    public static class RateLimit {
        private int capacity = 20;
        private int refillTokens = 20;
        private int refillPeriodSeconds = 60;

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }

        public int getRefillTokens() { return refillTokens; }
        public void setRefillTokens(int refillTokens) { this.refillTokens = refillTokens; }

        public int getRefillPeriodSeconds() { return refillPeriodSeconds; }
        public void setRefillPeriodSeconds(int refillPeriodSeconds) { this.refillPeriodSeconds = refillPeriodSeconds; }
    }
}
