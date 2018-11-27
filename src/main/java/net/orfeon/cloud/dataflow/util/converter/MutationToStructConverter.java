package net.orfeon.cloud.dataflow.util.converter;

import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.Value;
import com.google.cloud.spanner.ValueBinder;
import org.apache.beam.sdk.io.gcp.spanner.MutationGroup;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MutationToStructConverter {

    private MutationToStructConverter() {

    }

    public static List<Struct> convert(final MutationGroup mutationGroup) {
        final List<Struct> structs = mutationGroup.attached().stream()
                .map(MutationToStructConverter::convert)
                .collect(Collectors.toList());
        structs.add(convert(mutationGroup.primary()));
        return structs;
    }

    public static Struct convert(final Mutation mutation) {
        Struct.Builder builder = Struct.newBuilder();
        for(final Map.Entry<String, Value> column : mutation.asMap().entrySet()) {
            final Value value = column.getValue();
            final ValueBinder<Struct.Builder> binder = builder.set(column.getKey());
            switch(value.getType().getCode()) {
                case DATE:
                    builder = binder.to(value.isNull() ? null : value.getDate());
                    break;
                case INT64:
                    builder = binder.to(value.isNull() ? null : value.getInt64());
                    break;
                case STRING:
                    builder = binder.to(value.isNull() ? null : value.getString());
                    break;
                case TIMESTAMP:
                    builder = binder.to(value.isNull() ? null : value.getTimestamp());
                    break;
                case BOOL:
                    builder = binder.to(value.isNull() ? null : value.getBool());
                    break;
                case BYTES:
                    builder = binder.to(value.isNull() ? null : value.getBytes());
                    break;
                case FLOAT64:
                    builder = binder.to(value.isNull() ? null : value.getFloat64());
                    break;
                case STRUCT:
                    builder = binder.to(value.isNull() ? null : value.getStruct());
                    break;
                case ARRAY:
                    switch (value.getType().getArrayElementType().getCode()) {
                        case DATE:
                            builder = binder.toDateArray(value.isNull() ? null : value.getDateArray());
                            break;
                        case INT64:
                            builder = binder.toInt64Array(value.isNull() ? null : value.getInt64Array());
                            break;
                        case STRING:
                            builder = binder.toStringArray(value.isNull() ? null : value.getStringArray());
                            break;
                        case TIMESTAMP:
                            builder = binder.toTimestampArray(value.isNull() ? null : value.getTimestampArray());
                            break;
                        case BOOL:
                            builder = binder.toBoolArray(value.isNull() ? null : value.getBoolArray());
                            break;
                        case BYTES:
                            builder = binder.toBytesArray(value.isNull() ? null : value.getBytesArray());
                            break;
                        case FLOAT64:
                            builder = binder.toFloat64Array(value.isNull() ? null : value.getFloat64Array());
                            break;
                    }
                    break;
            }
        }
        return builder.build();
    }

}
