/*
 * Copyright 2023 Datastrato.
 * This software is licensed under the Apache License version 2.
 */
package com.datastrato.graviton.catalog.hive;

import static org.apache.hadoop.hive.metastore.TableType.EXTERNAL_TABLE;
import static org.apache.hadoop.hive.metastore.TableType.MANAGED_TABLE;

import com.datastrato.graviton.NameIdentifier;
import com.datastrato.graviton.catalog.hive.converter.FromHiveType;
import com.datastrato.graviton.catalog.hive.converter.ToHiveType;
import com.datastrato.graviton.meta.rel.BaseTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.ToString;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.Table;

/** Represents a Hive Table entity in the Hive Metastore catalog. */
@ToString
@Getter
public class HiveTable extends BaseTable {

  // A set of supported Hive table types.
  public static final Set<TableType> SUPPORT_TABLE_TYPES =
      Sets.newHashSet(MANAGED_TABLE, EXTERNAL_TABLE);

  // The key to store the Hive table comment in the parameters map.
  public static final String HMS_TABLE_COMMENT = "comment";

  private String inputFormat;

  private String outputFormat;

  private String serLib;

  private TableType tableType;

  private String location;

  private int createTime;

  private HiveTable() {}

  /**
   * Creates a new HiveTable instance from a Table and a Builder.
   *
   * @param table The inner Table representing the HiveTable.
   * @param builder The Builder used to construct the HiveTable.
   * @return A new HiveTable instance.
   */
  public static HiveTable fromInnerTable(Table table, Builder builder) {

    return builder
        .withComment(table.getParameters().get(HMS_TABLE_COMMENT))
        .withProperties(table.getParameters())
        .withTableType(TableType.valueOf(table.getTableType()))
        .withColumns(
            table.getSd().getCols().stream()
                .map(
                    f ->
                        new HiveColumn.Builder()
                            .withName(f.getName())
                            .withType(FromHiveType.convert(f.getType()))
                            .withComment(f.getComment())
                            .build())
                .toArray(HiveColumn[]::new))
        .withOutputFormat(table.getSd().getOutputFormat())
        .withInputFormat(table.getSd().getInputFormat())
        .withSerLib(table.getSd().getSerdeInfo().getSerializationLib())
        .withLocation(table.getSd().getLocation())
        .withCreateTime(table.getCreateTime())
        .build();
  }

  /**
   * Converts this HiveTable to its corresponding Table in the Hive Metastore.
   *
   * @return The converted Table.
   */
  public Table toInnerTable() {
    Table hiveTable = new Table();

    hiveTable.setTableName(name);
    hiveTable.setDbName(schemaIdentifier().name());
    hiveTable.setOwner(auditInfo.creator());
    hiveTable.setSd(buildStorageDescriptor());
    hiveTable.setTableType(String.valueOf(tableType));
    hiveTable.setParameters(properties);
    hiveTable.setCreateTime(createTime);
    hiveTable.setPartitionKeys(Lists.newArrayList() /* TODO(Minghuang): Add partition support */);

    return hiveTable;
  }

  private StorageDescriptor buildStorageDescriptor() {
    StorageDescriptor sd = new StorageDescriptor();
    sd.setCols(
        Arrays.stream(columns)
            .map(
                c ->
                    new FieldSchema(
                        c.name(),
                        c.dataType().accept(ToHiveType.INSTANCE).getQualifiedName(),
                        c.comment()))
            .collect(Collectors.toList()));
    sd.setLocation(location);
    sd.setInputFormat(inputFormat);
    sd.setOutputFormat(outputFormat);
    sd.setSerdeInfo(buildSerDeInfo());
    return sd;
  }

  private SerDeInfo buildSerDeInfo() {
    SerDeInfo serDeInfo = new SerDeInfo();
    serDeInfo.setName(name);
    serDeInfo.setSerializationLib(serLib);
    return serDeInfo;
  }

  /**
   * Gets the schema identifier for this HiveTable.
   *
   * @return The schema identifier.
   */
  public NameIdentifier schemaIdentifier() {
    return NameIdentifier.of(nameIdentifier().namespace().levels());
  }

  /** A builder class for constructing HiveTable instances. */
  public static class Builder extends BaseTableBuilder<Builder, HiveTable> {
    private String inputFormat;

    private String outputFormat;

    private String serLib;

    private TableType tableType;

    private String location;

    private int createTime;

    /**
     * Sets the input format for the HiveTable.
     *
     * @param inputFormat The input format to set.
     * @return The Builder instance.
     */
    public Builder withInputFormat(String inputFormat) {
      this.inputFormat = inputFormat;
      return this;
    }

    /**
     * Sets the output format for the HiveTable.
     *
     * @param outputFormat The output format to set.
     * @return The Builder instance.
     */
    public Builder withOutputFormat(String outputFormat) {
      this.outputFormat = outputFormat;
      return this;
    }

    public Builder withSerLib(String serLib) {
      this.serLib = serLib;
      return this;
    }

    /**
     * Sets the table type for the HiveTable.
     *
     * @param tableType The table type to set.
     * @return The Builder instance.
     */
    public Builder withTableType(TableType tableType) {
      this.tableType = tableType;
      return this;
    }

    public Builder withLocation(String location) {
      this.location = location;
      return this;
    }

    public Builder withCreateTime(int createTime) {
      this.createTime = createTime;
      return this;
    }

    /**
     * Internal method to build a HiveTable instance using the provided values.
     *
     * @return A new HiveTable instance with the configured values.
     */
    @Override
    protected HiveTable internalBuild() {
      HiveTable hiveTable = new HiveTable();
      hiveTable.id = id;
      hiveTable.schemaId = schemaId;
      hiveTable.namespace = namespace;
      hiveTable.name = name;
      hiveTable.comment = comment;
      hiveTable.properties = properties;
      hiveTable.auditInfo = auditInfo;
      hiveTable.columns = columns;
      hiveTable.inputFormat = inputFormat;
      hiveTable.outputFormat = outputFormat;
      hiveTable.serLib = serLib;
      hiveTable.location = location;
      hiveTable.createTime = createTime;
      hiveTable.tableType = tableType == null ? MANAGED_TABLE : tableType;

      // HMS put table comment in parameters
      hiveTable.properties.put(HMS_TABLE_COMMENT, comment);

      return hiveTable;
    }
  }
}