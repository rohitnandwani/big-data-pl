package profile

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.{StructField, StructType, StringType, IntegerType, LongType, TimestampType, ShortType, DoubleType}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.Row
import org.apache.spark.sql.DataFrame

object ProfileStackOverflow {
    /* 
     *  Data: StackOverflow
     *  Source: https://archive.org/details/stackexchange
     *  Size: 16GB
     *  Schema: https://meta.stackexchange.com/questions/2677/database-schema-documentation-for-the-public-data-dump-and-sede
    */

    // define path to data
    val basePath: String = "project/data/cleaned/"
    val baseSavePath: String = "project/data/stats/"

    /* 
        Profile Info
        ------------
        DataFrame: 
        1. Column Count
        2. Row Count

        Integer data columns:
        1. Max, Min
        2. Distinct

        String data columns:
        1. MaxLen, MinLen
        2. Distinct Values
        3. Number of Distinct
    */

    val postsSchema = StructType(Array(
        StructField("_AcceptedAnswerId", LongType, nullable = true),
        StructField("_AnswerCount", LongType, nullable = true),
        StructField("_Body", StringType, nullable = true),
        StructField("_ClosedDate", TimestampType, nullable = true), 
        StructField("_CommentCount", LongType, nullable = true),
        StructField("_CommunityOwnedDate", TimestampType, nullable = true), 
        StructField("_CreationDate", TimestampType, nullable = true), 
        StructField("_FavoriteCount", LongType, nullable = true),
        StructField("_Id", LongType, nullable = true),
        StructField("_LastActivityDate", TimestampType, nullable = true), 
        StructField("_LastEditDate", TimestampType, nullable = true),
        StructField("_LastEditorDisplayName", StringType, nullable = true),
        StructField("_LastEditorUserId", LongType, nullable = true),
        StructField("_OwnerDisplayName", StringType, nullable = true), 
        StructField("_OwnerUserId", LongType, nullable = true), 
        StructField("_ParentId", LongType, nullable = true), 
        StructField("_Score", LongType, nullable = true),
        StructField("_Tags", StringType, nullable = true),
        StructField("_Title", StringType, nullable = true), 
        StructField("_ViewCount", LongType, nullable = true), 
        StructField("_PostTypeId", LongType, nullable = true)
        StructField("_Tag", StringType, nullable = true)
        StructField("_CreationYear", IntegerType, nullable = true)
    )) 
    
    // reference for design
    // https://towardsdatascience.com/profiling-big-data-in-distributed-environment-using-spark-a-pyspark-data-primer-for-machine-78c52d0ce45
    val profileStatsSchema = StructType(Array(
        StructField("col_name", StringType, false),
        StructField("data_type", StringType, false),
        StructField("num_rows", LongType, false),
        StructField("num_nulls", LongType, false),
        StructField("num_spaces", LongType, false),
        StructField("num_blanks", LongType, false),
        StructField("count", LongType, false),
        StructField("min", IntegerType, false),
        StructField("max", IntegerType, false),
        StructField("num_distinct", LongType, false)
    ))

    def getStatsForCol(spark: SparkSession, df: DataFrame, colName: String): DataFrame = {
        val colType: String = df.schema(colName).dataType.toString
        val numRows: Long = df.count()
        val numNulls: Long = df.filter(df(colName).isNull || df(colName).isNaN).count()
        val numSpaces: Long = df.filter(df(colName) === " ").count()
        val numBlanks: Long = df.filter(df(colName) === "").count()
        val countProper: Long = numRows - numNulls - numSpaces - numBlanks
        val minMax = df.schema(colName).dataType match {
            case StringType => df.agg(min(length(col(colName))), max(length(col(colName)))).head()
            case _ => df.agg(min(colName), max(colName)).head()
        }
        val colMin: Int = minMax.getInt(0)
        val colMax: Int = minMax.getInt(1)
        val numDistinct: Long = df.agg(countDistinct(colName)).head().getLong(0)

        val newRow = Seq(Row(colName, colType, numRows, numNulls, numSpaces, numBlanks, countProper, colMin, colMax, numDistinct))
        val newRowDF = spark.createDataFrame(spark.sparkContext.parallelize(newRow), profileStatsSchema)
        return newRowDF
    }

    private def profilePostsData(spark: SparkSession): Unit = {
        val postsDF = spark.read.format("csv").schema(postsSchema).load(basePath + "posts.csv")
        postsDF.cache()
        
        val idStatsDF = getStatsForCol(spark, usersDF, "Id")
        val yearStatsDF = getStatsForCol(spark, usersDF, "_CreationYear")

        val emptyDF = spark.createDataFrame(spark.sparkContext.emptyRDD[Row], profileStatsSchema)
        val df1 = emptyDF.union(idStatsDF)
        val finalDF = df1.union(yearStatsDF)

        finalDF.coalesce(1).write.format("csv").mode("overwrite").option("header", "true").save(baseSavePath + "users_stats.csv")
    }


    def main(args: Array[String]): Unit = {
        val spark: SparkSession = SparkSession.builder.appName("ProfileStackOverflow").getOrCreate()

        profilePostsData(spark)
    }

}
