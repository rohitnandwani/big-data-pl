package etl

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{StructField, StructType, StringType, IntegerType, TimestampType, ShortType, DoubleType}

/** 
  * Data: Github
  * Source: https://ghtorrent.org/downloads.html
  * Size: 102 GB
  * Schema: https://ghtorrent.org/files/schema.pdf
  */
object TransformGithubRaw {

    // define path to data 
    val basePath: String = "project/data/"
    val rawDataPath: String = basePath + "raw/data/"
    val cleanedDataPath: String = basePath + "cleaned/"

    // define schema
    val usersSchema = StructType(Array(
        StructField("id", IntegerType, false),
        StructField("login", StringType, false),
        StructField("name", StringType, true),
        StructField("created_at", TimestampType, false),
        StructField("type", StringType, false),
        StructField("fake", ShortType, false),
        StructField("deleted", ShortType, false),
        StructField("long", DoubleType, true),
        StructField("lat", DoubleType, true),
        StructField("country_code", StringType, true),
        StructField("company", StringType, true),
        StructField("state", StringType, true),
        StructField("city", StringType, true)
    ))

    val commitsSchema = StructType(Array(
        StructField("id", IntegerType, false),
        StructField("sha", StringType, false),
        StructField("author_id", IntegerType, false),
        StructField("committer_id", IntegerType, false),
        StructField("project_id", IntegerType, false),
        StructField("created_at", TimestampType, false)
    ))

    val pullRequestsSchema = StructType(Array(
        StructField("id", IntegerType, false),
        StructField("head_repo_id", IntegerType, false),
        StructField("base_repo_id", IntegerType, false),
        StructField("head_commit_id", IntegerType, false),
        StructField("base_commit_id", IntegerType, false),
        StructField("pull_request_id", IntegerType, false),
        StructField("intra_branch", ShortType, true)
    ))

    val pullRequestsHistorySchema = StructType(Array(
        StructField("id", IntegerType, false),
        StructField("pull_request_id", IntegerType, false),
        StructField("created_at", TimestampType, false),
        StructField("action", StringType, false),
        StructField("actor_id", IntegerType, false)
    ))

    val projectsSchema = StructType(Array(
        StructField("id", IntegerType, false),
        StructField("url", StringType, false),
        StructField("owner_id", IntegerType, false),
        StructField("name", StringType, false),
        StructField("descriptor", StringType, false),
        StructField("language", StringType, false),
        StructField("created_at", TimestampType, false),
        StructField("forked_from", IntegerType, false),
        StructField("deleted", ShortType, false),
        StructField("updated_at", TimestampType, false)
    ))

    val projectLanguagesSchema = StructType(Array(
        StructField("project_id", IntegerType, false),
        StructField("language", StringType, false),
        StructField("bytes", IntegerType, true),
        StructField("created_at", TimestampType, false)
    ))

    val issuesSchema = StructType(Array(
        StructField("id", IntegerType, false),
        StructField("repo_id", IntegerType, false),
        StructField("reporter_id", IntegerType, false),
        StructField("assignee_id", IntegerType, false),
        StructField("issue_id", StringType, false),
        StructField("pull_request", ShortType, false),
        StructField("pull_request_id", IntegerType, false),
        StructField("created_at", TimestampType, false)
    ))

    val issueEventsSchema = StructType(Array(
        StructField("event_id", StringType, false),
        StructField("issue_id", IntegerType, false),
        StructField("actor_id", IntegerType, false),
        StructField("action", StringType, false),
        StructField("action_specific", StringType, false),
        StructField("created_at", TimestampType, false)
    ))

    // Load raw data from users.csv, drop unwanted columns and rows with null values.
    private def transformUsersData(spark: SparkSession): Unit = {
        val usersDF = spark.read.format("csv").schema(usersSchema).load(rawDataPath + "users.csv")
        val usersDF_dropped = usersDF.drop("login").drop("name").drop("type").drop("fake").drop("deleted").drop("long").drop("lat").drop("country_code").drop("company").drop("state").drop("city")
        val usersDF_nonull = usersDF_dropped.na.drop()      // remove null values
        val usersDF_cleaned = usersDF_nonull.withColumn("year", split(col("created_at"), "-")(0)).drop("created_at")   // convert timestamp to year
        
        // save cleaned data to hdfs
        usersDF_cleaned.write.format("csv").mode("overwrite").save(cleanedDataPath + "users.csv")
    }

    // Load raw data from projects.csv, drop unwanted columns and rows with null values.
    private def transformProjectsData(spark: SparkSession): Unit = {
        val projectsDF = spark.read.format("csv").schema(projectsSchema).load(rawDataPath + "projects.csv")
        val projectsDF_dropped = projectsDF.drop("url").drop("name").drop("descriptor").drop("forked_from").drop("deleted").drop("updated_at")
        val projectsDF_nonull = projectsDF_dropped.na.drop()    // remove null values
        val projectsDF_cleaned = projectsDF_nonull.filter(!projectsDF_nonull("language").contains("\\N")).withColumn("year", split(col("created_at"), "-")(0)).drop("created_at").withColumn("language", lower(col("language")))

        // save cleaned data to hdfs
        projectsDF_cleaned.write.format("csv").mode("overwrite").save(cleanedDataPath + "projects.csv")
    }

    // Load raw data from project_languages.csv, drop unwanted columns and rows with null values.
    private def transformProjectLangData(spark: SparkSession): Unit = {
        val projectLanguagesDF = spark.read.format("csv").schema(projectLanguagesSchema).load(rawDataPath + "project_languages.csv")
        val projectLanguagesDF_dropped = projectLanguagesDF.drop("bytes")    // drop unwanted columns
        val projectLanguagesDF_nonull = projectLanguagesDF_dropped.na.drop()    // remove null values
        val projectLanguagesDF_cleaned = projectLanguagesDF_nonull.withColumn("year", split(col("created_at"), "-")(0)).drop("created_at")
        
        // save cleaned data to hdfs
        projectLanguagesDF_cleaned.write.format("csv").mode("overwrite").save(cleanedDataPath + "project_languages.csv")
    }

    // Load raw data from pull_requests.csv, drop unwanted columns and rows with null values.
    private def transformPullRequestData(spark: SparkSession): Unit = {
        val pullRequestsDF = spark.read.format("csv").schema(pullRequestsSchema).load(rawDataPath + "pull_requests.csv")
        val pullRequestsDF_dropped = pullRequestsDF.drop("intra_branch")     // drop unwanted columns
        val pullRequestsDF_nonull = pullRequestsDF_dropped.na.drop()    // remove null values
        val pullRequestsDF_cleaned = pullRequestsDF_nonull
        
        // save cleaned data to hdfs
        pullRequestsDF_cleaned.write.format("csv").mode("overwrite").save(cleanedDataPath + "pull_requests.csv")
    }

    // Load raw data from commits.csv, drop unwanted columns and rows with null values, filter out commits with year <2007 and >2019
    private def transformCommitsData(spark: SparkSession): Unit = {
        val commitsDF = spark.read.format("csv").schema(commitsSchema).load(rawDataPath + "commits.csv")
        val commitsDF_dropped = commitsDF.drop("sha")    // drop unwanted columns    
        val commitsDF_nonull = commitsDF_dropped.na.drop()      // remove null values
        val commitsDF_cleaned = commitsDF_nonull.withColumn("year", split(col("created_at"), "-")(0)).drop("created_at")
        
        // Profiling revealed that commits have timestamps bw 1970 to 2038. This happens if clock is misconfigured
        // on the developer's workstation during commit (as recorded by Git).
        val commitsDF_filtered = commitsDF_cleaned.filter(col("year") >= 2007 && col("year") <= 2019)   // filter commits with 2007 <= year <= 2019

        // save cleaned data to hdfs
        commitsDF_filtered.write.format("csv").mode("overwrite").save(cleanedDataPath + "commits.csv")
    }

    // Load raw data from pull_request_history.csv, drop unwanted columns and rows with null values.
    private def transformPullRequestHistoryData(spark: SparkSession): Unit = {
        val pullRequestHistoryDF = spark.read.format("csv").schema(pullRequestsHistorySchema).load(rawDataPath + "pull_request_history.csv")
        val pullRequestHistoryDF_nonull = pullRequestHistoryDF.na.drop()    // remove null values
        val pullRequestHistoryDF_cleaned = pullRequestHistoryDF_nonull.withColumn("year", split(col("created_at"), "-")(0)).drop("created_at")
        
        // save cleaned data to hdfs
        pullRequestHistoryDF_cleaned.write.format("csv").mode("overwrite").save(cleanedDataPath + "pull_request_history.csv")
    }

    // Load raw data from issues.csv, drop unwanted columns and rows with null values.
    private def transformIssuesData(spark: SparkSession): Unit = {
        val issuesDF = spark.read.format("csv").schema(issuesSchema).load(rawDataPath + "issues.csv")
        val issuesDF_dropped = issuesDF.drop("reporter_id").drop("assignee_id").drop("pull_request").drop("pull_request_id")
        val issuesDF_nonull = issuesDF_dropped.na.drop()
        val issuesDF_cleaned = issuesDF_nonull

        // save cleaned data to hdfs
        issuesDF_cleaned.write.format("csv").mode("overwrite").save(cleanedDataPath + "issues.csv")
    }

    // Load raw data from issue_events.csv, drop unwanted columns and rows with null values.
    private def transformIssueEventsData(spark: SparkSession): Unit = {
        val issueEventsDF = spark.read.format("csv").schema(issueEventsSchema).load(rawDataPath + "issue_events.csv")
        val issueEventsDF_dropped = issueEventsDF.drop("actor_id").drop("action_specific")
        val issueEventsDF_nonull = issueEventsDF_dropped.na.drop()
        val issueEventsDF_cleaned = issueEventsDF_nonull

        // save cleaned data to hdfs
        issueEventsDF_cleaned.write.format("csv").mode("overwrite").save(cleanedDataPath + "issue_events.csv")
    }

    def main(args: Array[String]): Unit = {
        val spark = SparkSession.builder
        .appName("TransformGithubRaw")
        .getOrCreate()
        
        // transformUsersData(spark)
        // transformProjectsData(spark)
        // transformProjectLangData(spark)
        // transformPullRequestData(spark)
        // transformCommitsData(spark)
        // transformPullRequestHistoryData(spark)
        transformIssuesData(spark)
        transformIssueEventsData(spark)
    }

}
