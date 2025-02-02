package tech.ydb.topic.read.impl;

import java.time.Instant;
import java.util.Map;

import tech.ydb.core.utils.ProtobufUtils;
import tech.ydb.topic.YdbTopic;
import tech.ydb.topic.description.Codec;

/**
 * @author Nikolay Perfilov
 */
public class BatchMeta {
    private final String producerId;
    private final Map<String, String> writeSessionMeta;
    private final Codec codec;
    private final Instant writtenAt;

    public BatchMeta(YdbTopic.StreamReadMessage.ReadResponse.Batch batch) {
        this.producerId = batch.getProducerId();
        this.writeSessionMeta = batch.getWriteSessionMetaMap();
        this.codec = tech.ydb.topic.utils.ProtoUtils.codecFromProto(batch.getCodec());
        this.writtenAt = ProtobufUtils.protoToInstant(batch.getWrittenAt());
    }

    public String getProducerId() {
        return producerId;
    }

    public Map<String, String> getWriteSessionMeta() {
        return writeSessionMeta;
    }

    public Codec getCodec() {
        return codec;
    }

    public Instant getWrittenAt() {
        return writtenAt;
    }
}
