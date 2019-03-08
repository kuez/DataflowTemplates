package net.orfeon.cloud.dataflow.util.converter;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.beam.sdk.io.gcp.bigquery.SchemaAndRecord;
import org.apache.beam.sdk.options.ValueProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class RecordToTableRowConverter {

    private static final Logger LOG = LoggerFactory.getLogger(RecordToTableRowConverter.class);

    private enum TableRowFieldType {
        STRING,
        BYTES,
        INT64,
        FLOAT64,
        NUMERIC,
        BOOL,
        DATE,
        TIME,
        DATETIME,
        TIMESTAMP,
        GEOGRAPHY,
        ARRAY,
        STRUCT
    }

    private enum TableRowFieldMode {
        REQUIRED,
        NULLABLE,
        REPEATED
    }

    public static TableRow convert(GenericRecord record) {
        final TableRow row = new TableRow();
        for(final Schema.Field field : record.getSchema().getFields()) {
            row.set(field.name(), convertTableRowValue(field.schema(), record.get(field.name())));
        }
        return row;
    }

    public static TableRow convert(SchemaAndRecord record) {
        return convert(record.getRecord());
    }

    public static TableSchema convertTableSchema(final Schema schema) {
        final List<TableFieldSchema> structFields = schema.getFields().stream()
                .map(field -> getFieldTableSchema(field.name(), field.schema()))
                .filter(fieldSchema -> fieldSchema != null)
                .collect(Collectors.toList());
        return new TableSchema().setFields(structFields);
    }

    public static TableSchema convertTableSchema(final GenericRecord record) {
        return convertTableSchema(record.getSchema());
    }

    // for BigQueryIO.Write.withSchemaFromView
    public static Map<String, String> convertTableSchema(final ValueProvider<String> output, final Schema schema) {
        final TableSchema tableSchema = convertTableSchema(schema);
        final String json = new Gson().toJson(tableSchema);
        LOG.info(String.format("Spanner Query Result Schema Json: %s", json));
        final Map<String,String> map = new HashMap<>();
        map.put(output.get(), json);
        return map;
    }

    // for BigQueryIO.Write.withSchemaFromView
    public static Map<String, String> convertTableSchema(final ValueProvider<String> output, final GenericRecord record) {
        return convertTableSchema(output, record.getSchema());
    }


    private static TableFieldSchema getFieldTableSchema(final String fieldName, final Schema schema) {
        return getFieldTableSchema(fieldName, schema, TableRowFieldMode.REQUIRED);
    }

    private static TableFieldSchema getFieldTableSchema(final String fieldName, final Schema schema, final TableRowFieldMode mode) {
        switch (schema.getType()) {
            case ENUM:
            case STRING:
                return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.STRING.name()).setMode(mode.name());
            case FIXED:
            case BYTES:
                final Map<String,Object> props = schema.getObjectProps();
                final int scale = props.containsKey("scale") ? Integer.valueOf(props.get("scale").toString()) : 0;
                final int precision = props.containsKey("precision") ? Integer.valueOf(props.get("precision").toString()) : 0;
                if (LogicalTypes.decimal(precision, scale).equals(schema.getLogicalType())) {
                    return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.NUMERIC.name()).setMode(mode.name());
                }
                return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.BYTES.name()).setMode(mode.name());
            case INT:
                if (LogicalTypes.date().equals(schema.getLogicalType())) {
                    return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.DATE.name()).setMode(mode.name());
                } else if (LogicalTypes.timeMillis().equals(schema.getLogicalType())) {
                    return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.TIME.name()).setMode(mode.name());
                }
                return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.INT64.name()).setMode(mode.name());
            case LONG:
                if (LogicalTypes.timestampMillis().equals(schema.getLogicalType())
                        || LogicalTypes.timestampMicros().equals(schema.getLogicalType())) {
                    return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.TIMESTAMP.name()).setMode(mode.name());
                } else if(LogicalTypes.timeMicros().equals(schema.getLogicalType())) {
                    return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.TIME.name()).setMode(mode.name());
                }
                return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.INT64.name()).setMode(mode.name());
            case FLOAT:
            case DOUBLE:
                return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.FLOAT64.name()).setMode(mode.name());
            case BOOLEAN:
                return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.BOOL.name()).setMode(mode.name());
            case RECORD:
                final List<TableFieldSchema> structFieldSchemas = schema.getFields().stream()
                        .map(field -> getFieldTableSchema(field.name(), field.schema()))
                        .collect(Collectors.toList());
                return new TableFieldSchema().setName(fieldName).setType(TableRowFieldType.STRUCT.name()).setFields(structFieldSchemas).setMode(mode.name());
            case MAP:
                final List<TableFieldSchema> mapFieldSchemas = ImmutableList.of(
                        new TableFieldSchema().setName("key").setType(TableRowFieldType.STRING.name()).setMode(TableRowFieldMode.REQUIRED.name()),
                        getFieldTableSchema("value", schema.getValueType()));
                        //addMapValueType(new TableFieldSchema().setName("value"), schema.getValueType()));
                return new TableFieldSchema()
                        .setName(fieldName)
                        .setType(TableRowFieldType.STRUCT.name())
                        .setFields(mapFieldSchemas)
                        .setMode(TableRowFieldMode.REPEATED.name());
            case UNION:
                for (final Schema childSchema : schema.getTypes()) {
                    if (Schema.Type.NULL.equals(childSchema.getType())) {
                        continue;
                    }
                    return getFieldTableSchema(fieldName, childSchema, TableRowFieldMode.NULLABLE);
                }
                throw new IllegalArgumentException();
            case ARRAY:
                return getFieldTableSchema(fieldName, schema.getElementType()).setMode(TableRowFieldMode.REPEATED.name());
            case NULL:
                // BigQuery ignores NULL value
                // https://cloud.google.com/bigquery/data-formats#avro_format
                throw new IllegalArgumentException();
            default:
                throw new IllegalArgumentException();
        }
    }

    private static Object convertTableRowValue(final Schema schema, final Object value) {
        return convertTableRowValue(schema, value, false);
    }

    private static Object convertTableRowValues(final Schema schema, final Object value) {
        return convertTableRowValue(schema, value, true);
    }

    private static Object convertTableRowValue(final Schema schema, final Object value, final boolean isArray) {
        if(value == null) {
            return null;
        }
        if(isArray) {
            return ((List<Object>)value).stream()
                    .map(v -> convertTableRowValue(schema, v))
                    .filter(v -> v != null)
                    .collect(Collectors.toList());
        }
        switch(schema.getType()) {
            case ENUM:
            case STRING:
                return value.toString();
            case FIXED:
            case BYTES:
                final Map<String,Object> props = schema.getObjectProps();
                final int scale = props.containsKey("scale") ? Integer.valueOf(props.get("scale").toString()) : 0;
                final int precision = props.containsKey("precision") ? Integer.valueOf(props.get("precision").toString()) : -1;
                if(LogicalTypes.decimal(precision, scale).equals(schema.getLogicalType())) {
                    final byte[] bytes;
                    if(Schema.Type.FIXED.equals(schema.getType())) {
                        bytes = ((GenericData.Fixed)value).bytes();
                    } else {
                        bytes = ((ByteBuffer)value).array();
                    }
                    if(bytes.length == 0) {
                        return BigDecimal.valueOf(0, 0);
                    }
                    return BigDecimal.valueOf(new BigInteger(bytes).longValue(), scale);
                }
                if(Schema.Type.FIXED.equals(schema.getType())) {
                    return ByteBuffer.wrap(((GenericData.Fixed)value).bytes());
                }
                return value;
            case INT:
                if(LogicalTypes.date().equals(schema.getLogicalType())) {
                    final LocalDate localDate = LocalDate.ofEpochDay((Integer)value);
                    return localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                } else if(LogicalTypes.timeMillis().equals(schema.getLogicalType())) {
                    final Long intValue = new Long((Integer)value);
                    final LocalTime localTime = LocalTime.ofNanoOfDay(intValue * 1000 * 1000);
                    return localTime.format(DateTimeFormatter.ISO_LOCAL_TIME);
                }
                return value;
            case LONG:
                final Long longValue = (Long)value;
                if(LogicalTypes.timestampMillis().equals(schema.getLogicalType())) {
                    return longValue / 1000;
                } else if(LogicalTypes.timestampMicros().equals(schema.getLogicalType())) {
                    return longValue / 1000000;
                } else if(LogicalTypes.timeMicros().equals(schema.getLogicalType())) {
                    LocalTime time = LocalTime.ofNanoOfDay(longValue * 1000);
                    return time.format(DateTimeFormatter.ISO_LOCAL_TIME);
                }
                return value;
            case FLOAT:
            case DOUBLE:
            case BOOLEAN:
                return value;
            case RECORD:
                return convert((GenericRecord) value);
            case MAP:
                final Map<Object, Object> map = (Map)value;
                return map.entrySet().stream()
                        .map(entry -> new TableRow()
                                .set("key", entry.getKey() == null ? "" : entry.getKey().toString())
                                .set("value", convertTableRowValue(schema.getValueType(), entry.getValue())))
                        .collect(Collectors.toList());
            case UNION:
                for(final Schema childSchema : schema.getTypes()) {
                    if (Schema.Type.NULL.equals(childSchema.getType())) {
                        continue;
                    }
                    return convertTableRowValue(childSchema, value);
                }
            case ARRAY:
                return convertTableRowValues(schema.getElementType(), value);
            default:
                return value;
        }
    }

}
