@file:Suppress("MagicNumber") // Column lengths match Spring Batch's official schema definition

package com.kludgeworks.eu.legislation.formex.downloader.domain.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Exposed table definitions for Spring Batch metadata tables.
 *
 * These tables are created and managed by Spring Batch's schema initialization,
 * but we define them here using Exposed's DSL to enable type-safe queries.
 *
 * Note: We only define the columns we need to query. Spring Batch manages
 * the full schema including indexes and constraints.
 */
object BatchJobInstance : Table("BATCH_JOB_INSTANCE") {
    val jobInstanceId = long("JOB_INSTANCE_ID")
    val jobName = varchar("JOB_NAME", 100)

    override val primaryKey = PrimaryKey(jobInstanceId)
}

/**
 * Spring Batch job execution table.
 *
 * A job execution represents a single attempt to run a job instance.
 * Contains status (STARTED, COMPLETED, FAILED, etc.) and timing information.
 */
object BatchJobExecution : Table("BATCH_JOB_EXECUTION") {
    val jobExecutionId = long("JOB_EXECUTION_ID")
    val jobInstanceId = long("JOB_INSTANCE_ID").references(BatchJobInstance.jobInstanceId)
    val status = varchar("STATUS", 10)
    val startTime = timestamp("START_TIME").nullable()
    val endTime = timestamp("END_TIME").nullable()
    val exitCode = varchar("EXIT_CODE", 2500).nullable()
    val exitMessage = varchar("EXIT_MESSAGE", 2500).nullable()

    override val primaryKey = PrimaryKey(jobExecutionId)
}

/**
 * Spring Batch step execution table.
 *
 * A step execution represents a single attempt to execute a step within a job.
 * Multiple step executions can exist for a single job execution.
 */
object BatchStepExecution : Table("BATCH_STEP_EXECUTION") {
    val stepExecutionId = long("STEP_EXECUTION_ID")
    val jobExecutionId = long("JOB_EXECUTION_ID").references(BatchJobExecution.jobExecutionId)
    val stepName = varchar("STEP_NAME", 100)
    val status = varchar("STATUS", 10)
    val startTime = timestamp("START_TIME").nullable()
    val endTime = timestamp("END_TIME").nullable()
    val exitCode = varchar("EXIT_CODE", 2500).nullable()
    val exitMessage = varchar("EXIT_MESSAGE", 2500).nullable()

    override val primaryKey = PrimaryKey(stepExecutionId)
}
