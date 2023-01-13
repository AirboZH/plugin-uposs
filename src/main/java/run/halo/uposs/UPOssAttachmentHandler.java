package run.halo.uposs;

import com.upyun.RestManager;
import com.upyun.UpException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext).filter(context -> this.shouldHandle(context.policy()))
            .doOnNext(context -> {
                var annotations = context.attachment().getMetadata().getAnnotations();
                if (annotations == null || !annotations.containsKey(OBJECT_KEY)) {
                    return;
                }
                var objectName = annotations.get(OBJECT_KEY);
                var properties = getProperties(deleteContext.configMap());
                var client = getManager(properties);
                var location = properties.getLocation();

                log.info("{}/{} is being deleted from UPYunOSS", location,
                    objectName);

                try {
                    var res = client.deleteFile(location + "/" + objectName, null);
                    log.info("UPYun response: {}", res);
                    if (!res.isSuccessful()) {
                        return;
                    }
                } catch (IOException | UpException e) {
                    throw new RuntimeException(e);
                }

                log.info("{}/{} was deleted successfully from UPYunOSS", location,
                    objectName);
            }).map(DeleteContext::attachment);
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
        var client = this.getManager(properties);
        var filename = uploadContext.file().filename();
        var location = properties.getLocation();
        var contentType = MediaTypeFactory.getMediaType(filename);
        log.info("UPYun properties: filename: {}, location: {}, contentType: {}", filename,
            location, contentType);

        return Mono.fromCallable(() -> {
            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos);
            DataBufferUtils.write(uploadContext.file().content(), pos)
                .subscribeOn(Schedulers.boundedElastic()).doOnComplete(() -> {
                    try {
                        pos.close();
                    } catch (IOException ioe) {
                        log.warn("Failed to close output stream", ioe);
                    }

                }).subscribe(DataBufferUtils.releaseConsumer());
            ByteArrayOutputStream outputStream = this.cloneInputStream(pis);
            if (outputStream != null) {
                InputStream in1 = new ByteArrayInputStream(outputStream.toByteArray());
                InputStream in2 = new ByteArrayInputStream(outputStream.toByteArray());
                int contentLength = in1.readAllBytes().length;
                in1.close();

                Map<String, String> params = new HashMap<>();
                params.put("Content-Length", String.valueOf(contentLength));

                var res = client.writeFile(location + "/" + filename, in2, params);
                log.info("UPYunOss Response: {}", res);
                in2.close();

                if (res.isSuccessful()) {
                    return new ObjectDetail(location, filename, contentType.toString(),
                        contentLength);
                } else if (res.code() == 401) {
                    throw new RuntimeException("UPYun Authentication Failed");
                } else {
                    throw new RuntimeException("Upload Failed");
                }
            } else {
                throw new RuntimeException("CloneInputStream is Null");
            }
        }).subscribeOn(Schedulers.boundedElastic());
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
