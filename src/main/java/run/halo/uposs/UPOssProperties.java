package run.halo.uposs;

import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class UPOssProperties {
    private Protocol protocol = Protocol.https;
    private String domain;
    private String bucket;
    private String operator_name;
    private String operator_password;
    private String location;

    public void setDomain(String domain) {
        this.domain = UrlUtils.removeHttpPrefix(domain);
    }

    public String getLocation() {
        return "/" + this.location;
    }

    public void setLocation(String location) {
        final var fileSeparator = "/";
        if (StringUtils.hasText(location)) {
            if (location.startsWith(fileSeparator)) {
                location = location.substring(1);
            }
            if (location.endsWith(fileSeparator)) {
                location = location.substring(0, location.length() - 1);
            }
        } else {
            location = "";
        }
        this.location = location;
    }

    enum Protocol {
        http, https
    }
}
