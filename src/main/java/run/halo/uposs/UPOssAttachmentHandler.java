package run.halo.uposs;

import com.upyun.RestManager;
import com.upyun.UpException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Constant;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.infra.utils.JsonUtils;

@Slf4j
@Extension
public class UPOssAttachmentHandler implements AttachmentHandler {

    private static final String OBJECT_KEY = "uposs.plugin.halo.run/object-key";

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext)
            .filter(context -> this.shouldHandle(context.policy()))
            .flatMap(context -> {
                final var properties = getProperties(context.configMap());
                return upload(context, properties)
                    .map(objectDetail -> this.buildAttachment(context, properties, objectDetail));
            });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext context) {
        // TODO
        return null;
    }

    UPOssProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, UPOssProperties.class);
    }

    Attachment buildAttachment(UploadContext uploadContext, UPOssProperties properties,
                               ObjectDetail objectDetail) {
        var location = properties.getLocation();
        var externalLink = properties.getProtocol() + "://" + properties.getDomain() +
            location + "/" + objectDetail.objectName();

        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        metadata.setAnnotations(
            Map.of(OBJECT_KEY, objectDetail.objectName(), Constant.EXTERNAL_LINK_ANNO_KEY,
                UriUtils.encodePath(externalLink, StandardCharsets.UTF_8)));


        var spec = new Attachment.AttachmentSpec();
        spec.setSize((long) objectDetail.contentLength());
        spec.setDisplayName(uploadContext.file().filename());
        spec.setMediaType(objectDetail.contentType);

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        return attachment;
    }

    RestManager getManager(UPOssProperties properties) {
        return new RestManager(properties.getBucket(), properties.getOperator_name(),
            properties.getOperator_password());
    }

    private Mono<ObjectDetail> upload(UploadContext uploadContext, UPOssProperties properties) {
        var client = getManager(properties);
        var filename = uploadContext.file().filename();
        var location = properties.getLocation();
        var bufferFlux = uploadContext.file().content();
        log.info("UPYun properties: filename: {},location: {}", filename, location);
        return bufferFlux
            .map(DataBuffer::asInputStream).reduce(SequenceInputStream::new)
            .flatMap(inputStream -> {
                var byteArrayOutputStream = cloneInputStream(inputStream);
                var contentType = MediaTypeFactory.getMediaType(filename);
                var ins = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                var contentLength = byteArrayOutputStream.size();

                Map<String, String> params = new HashMap<>();
                params.put("Content-Length", String.valueOf(contentLength));

                try {
                    var res = client.writeFile(location + "/" + filename, ins, params);
                    log.info("UPYun response: {}", res);
                } catch (IOException | UpException e) {
                    return Mono.error(new RuntimeException(e));
                }
                return Mono.justOrEmpty(
                    new ObjectDetail(location, filename, contentType.toString(), contentLength));
            });

    }


    boolean shouldHandle(Policy policy) {
        if (policy == null || policy.getSpec() == null ||
            policy.getSpec().getTemplateName() == null) {
            return false;
        }
        String templateName = policy.getSpec().getTemplateName();
        return "uposs".equals(templateName);
    }

    private ByteArrayOutputStream cloneInputStream(InputStream input) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = input.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            return baos;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    record ObjectDetail(String location, String objectName, String contentType,
                        int contentLength) {
    }

}
