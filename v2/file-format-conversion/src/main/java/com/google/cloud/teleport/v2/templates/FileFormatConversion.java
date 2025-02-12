/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.v2.templates;

import com.google.cloud.teleport.metadata.Template;
import com.google.cloud.teleport.metadata.TemplateCategory;
import com.google.cloud.teleport.metadata.TemplateParameter;
import com.google.cloud.teleport.v2.common.UncaughtExceptionLogger;
import com.google.cloud.teleport.v2.templates.FileFormatConversion.FileFormatConversionOptions;
import com.google.cloud.teleport.v2.transforms.AvroConverters.AvroOptions;
import com.google.cloud.teleport.v2.transforms.CsvConverters.CsvPipelineOptions;
import com.google.cloud.teleport.v2.transforms.ParquetConverters.ParquetOptions;
import java.util.EnumMap;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation.Required;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link FileFormatConversion} pipeline takes in an input file, converts it to a desired format
 * and saves it to Cloud Storage. Supported file transformations are:
 *
 * <ul>
 *   <li>Csv to Avro
 *   <li>Csv to Parquet
 *   <li>Avro to Parquet
 *   <li>Parquet to Avro
 * </ul>
 *
 * <p><b>Pipeline Requirements</b>
 *
 * <ul>
 *   <li>Input file exists in Google Cloud Storage.
 *   <li>Google Cloud Storage output bucket exists.
 * </ul>
 *
 * <p><b>Example Usage</b>
 *
 * <pre>
 * # Set the pipeline vars
 * PROJECT=my-project
 * BUCKET_NAME=my-bucket
 *
 * # Set containerization vars
 * IMAGE_NAME=my-image-name
 * TARGET_GCR_IMAGE=gcr.io/${PROJECT}/${IMAGE_NAME}
 * BASE_CONTAINER_IMAGE=my-base-container-image
 * BASE_CONTAINER_IMAGE_VERSION=my-base-container-image-version
 * APP_ROOT=/path/to/app-root
 * COMMAND_SPEC=/path/to/command-spec
 *
 * # Set vars for execution
 * export INPUT_FILE_FORMAT=Csv
 * export OUTPUT_FILE_FORMAT=Avro
 * export AVRO_SCHEMA_PATH=gs://path/to/avro/schema
 * export HEADERS=false
 * export DELIMITER=","
 *
 * # Build and upload image
 * mvn clean package \
 * -Dimage=${TARGET_GCR_IMAGE} \
 * -Dbase-container-image=${BASE_CONTAINER_IMAGE} \
 * -Dbase-container-image.version=${BASE_CONTAINER_IMAGE_VERSION} \
 * -Dapp-root=${APP_ROOT} \
 * -Dcommand-spec=${COMMAND_SPEC}
 *
 * # Create an image spec in GCS that contains the path to the image
 * {
 *    "docker_template_spec": {
 *       "docker_image": $TARGET_GCR_IMAGE
 *     }
 *  }
 *
 * # Execute template:
 * API_ROOT_URL="https://dataflow.googleapis.com"
 * TEMPLATES_LAUNCH_API="${API_ROOT_URL}/v1b3/projects/${PROJECT}/templates:launch"
 * JOB_NAME="csv-to-avro-`date +%Y%m%d-%H%M%S-%N`"
 *
 * time curl -X POST -H "Content-Type: application/json"     \
 *     -H "Authorization: Bearer $(gcloud auth print-access-token)" \
 *     "${TEMPLATES_LAUNCH_API}"`
 *     `"?validateOnly=false"`
 *     `"&dynamicTemplate.gcsPath=${BUCKET_NAME}/path/to/image-spec"`
 *     `"&dynamicTemplate.stagingLocation=${BUCKET_NAME}/staging" \
 *     -d '
 *      {
 *       "jobName":"'$JOB_NAME'",
 *       "parameters": {
 *            "inputFileFormat":"'$INPUT_FILE_FORMAT'",
 *            "outputFileFormat":"'$OUTPUT_FILE_FORMAT'",
 *            "inputFileSpec":"'$BUCKET_NAME/path/to/input-file'",
 *            "outputBucket":"'$BUCKET_NAME/path/to/output-location/'",
 *            "containsHeaders":"'$HEADERS'",
 *            "schema":"'$AVRO_SCHEMA_PATH'",
 *            "outputFilePrefix":"output-file",
 *            "numShards":"3",
 *            "delimiter":"'$DELIMITER'"
 *         }
 *       }
 *      '
 * </pre>
 */
@Template(
    name = "File_Format_Conversion",
    category = TemplateCategory.UTILITIES,
    displayName = "Convert file formats between Avro, Parquet & CSV",
    description = "A pipeline to convert file formats between Avro, Parquet & csv.",
    optionsClass = FileFormatConversionOptions.class,
    optionalOptions = {"deadletterTable"},
    flexContainerName = "file-format-conversion",
    documentation =
        "https://cloud.google.com/dataflow/docs/guides/templates/provided/file-format-conversion",
    contactInformation = "https://cloud.google.com/support")
public class FileFormatConversion {

  /** Logger for class. */
  private static final Logger LOG = LoggerFactory.getLogger(FileFormatConversionFactory.class);

  private static EnumMap<ValidFileFormats, String> validFileFormats =
      new EnumMap<ValidFileFormats, String>(ValidFileFormats.class);

  /**
   * The {@link FileFormatConversionOptions} provides the custom execution options passed by the
   * executor at the command-line.
   */
  public interface FileFormatConversionOptions
      extends PipelineOptions, CsvPipelineOptions, AvroOptions, ParquetOptions {
    @TemplateParameter.Enum(
        order = 1,
        enumOptions = {"avro", "csv", "parquet"},
        description = "File format of the input files.",
        helpText = "File format of the input files. Needs to be either avro, parquet or csv.")
    @Required
    String getInputFileFormat();

    void setInputFileFormat(String inputFileFormat);

    @TemplateParameter.Enum(
        order = 2,
        enumOptions = {"avro", "parquet"},
        description = "File format of the output files.",
        helpText = "File format of the output files. Needs to be either avro or parquet.")
    @Required
    String getOutputFileFormat();

    void setOutputFileFormat(String outputFileFormat);
  }

  /** The {@link ValidFileFormats} enum contains all valid file formats. */
  public enum ValidFileFormats {
    CSV,
    AVRO,
    PARQUET
  }

  /**
   * Main entry point for pipeline execution.
   *
   * @param args Command line arguments to the pipeline.
   */
  public static void main(String[] args) {
    UncaughtExceptionLogger.register();

    FileFormatConversionOptions options =
        PipelineOptionsFactory.fromArgs(args)
            .withValidation()
            .as(FileFormatConversionOptions.class);

    run(options);
  }

  /**
   * Runs the pipeline to completion with the specified options.
   *
   * @param options The execution options.
   * @return The pipeline result.
   * @throws RuntimeException thrown if incorrect file formats are passed.
   */
  public static PipelineResult run(FileFormatConversionOptions options) {
    String inputFileFormat = options.getInputFileFormat().toUpperCase();
    String outputFileFormat = options.getOutputFileFormat().toUpperCase();

    validFileFormats.put(ValidFileFormats.CSV, "CSV");
    validFileFormats.put(ValidFileFormats.AVRO, "AVRO");
    validFileFormats.put(ValidFileFormats.PARQUET, "PARQUET");

    if (!validFileFormats.containsValue(inputFileFormat)) {
      throw new IllegalArgumentException("Invalid input file format.");
    }
    if (!validFileFormats.containsValue(outputFileFormat)) {
      throw new IllegalArgumentException("Invalid output file format.");
    }
    if (inputFileFormat.equals(outputFileFormat)) {
      throw new IllegalArgumentException("Input and output file format cannot be the same.");
    }

    // Create the pipeline
    Pipeline pipeline = Pipeline.create(options);

    pipeline.apply(
        inputFileFormat + " to " + outputFileFormat,
        FileFormatConversionFactory.FileFormat.newBuilder()
            .setOptions(options)
            .setInputFileFormat(inputFileFormat)
            .setOutputFileFormat(outputFileFormat)
            .build());

    return pipeline.run();
  }
}
