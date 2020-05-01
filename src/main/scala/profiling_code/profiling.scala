package profile

import org.apache.spark.sql.types.{StructField, StructType, StringType, IntegerType, TimestampType, ShortType, DoubleType}
import org.apache.spark.sql.functions.lower

class ProfileDataset(var path: String) {
    /* 
    Github data
    Source: https://ghtorrent.org/downloads.html
    Size: 102 GB
    Schema: https://ghtorrent.org/files/schema.pdf

    The data is distributed among multiple csv files (one table per file). The relevant csv files are:
    1. users.csv
    2. commits.csv
    3. pull_requests.csv
    4. project_languages.csv
    */

    // set path to data
    val data_path: String = "project/data/raw/data/"
    // val data_path: String = "project/data/test/"

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

    // load dataframe
    val usersDF = spark.read.format("csv").schema(usersSchema).load(data_path + "users.csv")
    val commitsDF = spark.read.format("csv").schema(commitsSchema).load(data_path + "commits.csv")
    val pullRequestsDF = spark.read.format("csv").schema(pullRequestsSchema).load(data_path + "pull_requests.csv")
    val projectsDF = spark.read.format("csv").schema(projectsSchema).load(data_path + "projects.csv")
    val projectLanguagesDF = spark.read.format("csv").schema(projectLanguagesSchema).load(data_path + "project_languages.csv")

    // initial count
    val users_count_pre = usersDF.count()
    val commits_count_pre = commitsDF.count()
    val pull_requests_pre = pullRequestsDF.count()
    val project_languages_pre = projectLanguagesDF.count()

    // cleaning - drop columns
    val usersDF_clean = usersDF.drop("login").drop("name").drop("created_at").drop("type").drop("fake").drop("deleted").drop("long").drop("lat").drop("country_code").drop("company").drop("state").drop("city")
    val commitsDF_clean = commitsDF.drop("sha")
    val pullRequestsDF_clean = pullRequestsDF.drop("head_repo_id").drop("base_repo_id").drop("head_commit_id").drop("base_commit_id").drop("intra_branch")
    val projectsDF_clean = projectsDF.drop("url").drop("owner_id").drop("name").drop("descriptor").drop("forked_from").drop("deleted").drop("updated_at").na.drop("any")
    val projectLanguagesDF_clean = projectLanguagesDF.drop("bytes").drop("created_at")

    val projectsDF_clean_2 = projectsDF_clean.withColumn("language", lower(col("language")))
    projectsDF_clean_2.show()


    usersDF_clean.take(10).foreach(println)
    commitsDF_clean.take(10).foreach(println)
    pullRequestsDF_clean.take(10).foreach(println)
    projectsDF_clean.take(10).foreach(println)
    projectLanguagesDF_clean.take(10).foreach(println)


    val usersDF_cleaned = usersDF_clean.take(1000)
    val commitsDF_cleaned = commitsDF_clean.take(10000)
    val pullRequestsDF_cleaned = pullRequestsDF_clean.take(5000)
    val projectsDF_cleaned = projectsDF_clean.take(1000)
    val projectLanguagesDF_cleaned = projectLanguagesDF_clean.take(1000)

    /*
    project_languages -> 
    do flatmap on (id, [languages]) and make tuple (id, language)
    */

    val languagesDF = projectLanguagesDF_clean.select("language").distinct()
    languagesDF.collect().foreach(println)
    languagesDF.count()
    // number of languages on Github = 386
    languagesDF.coalesce(1).write.format("csv").mode("overwrite").save("project/data/cleaned/languages.csv")

    // project_id, list of languages

    /*  Profiling project_languages.csv
    projectLanguagesDF_clean.select(countDistinct("project_id")).show()
    projectLanguagesDF_clean.select("language").distinct().show()
    total count = 138205530
    project_id -> 
        unique count = 41659371
    languages ->
    */

    projectLanguagesDF_clean.join(commitsDF_clean, projectLanguagesDF_clean.col("project_id") ===commitsDF_clean.col("project_id")).take(10).show()

    // final count
    val users_count_post = usersDF_clean.count()
    val commits_count_post = commitsDF_clean.count()
    val pull_requests_post = pullRequestsDF_clean.count()
    val project_languages_post = projectLanguagesDF_clean.count()

    // save cleaned data
    usersDF_clean.write.format("csv").option("path", data_path + "cleaned/users.csv").save()
    commitsDF_clean.write.format("csv").option("path", data_path + "cleaned/commits.csv").save()
    pullRequestsDF_clean.write.format("csv").option("path", data_path + "cleaned/pull_requests.csv").save()
    projectLanguagesDF_clean.write.format("csv").option("path", data_path + "cleaned/project_languages.csv").save()
}
