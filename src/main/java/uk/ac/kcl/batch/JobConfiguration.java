/* 
 * Copyright 2016 King's College London, Richard Jackson <richgjackson@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.kcl.batch;


import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.integration.partition.BeanFactoryStepLocator;
import org.springframework.batch.integration.partition.StepExecutionRequestHandler;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.batch.item.support.CompositeItemProcessor;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.batch.support.DatabaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.RowMapper;
import uk.ac.kcl.exception.WebserviceProcessingFailedException;
import uk.ac.kcl.itemProcessors.JSONMakerItemProcessor;
import uk.ac.kcl.model.Document;
import uk.ac.kcl.partitioners.StepPartitioner;
import uk.ac.kcl.utils.LoggerHelper;

import javax.sql.DataSource;
import java.util.ArrayList;

/**
 *
 * @author rich
 */

@Configuration
@ComponentScan({"uk.ac.kcl.rowmappers",
        "uk.ac.kcl.utils",
        "uk.ac.kcl.listeners",
        "uk.ac.kcl.partitioners",
        "uk.ac.kcl.itemReaders",
        "uk.ac.kcl.itemProcessors",
        "uk.ac.kcl.itemWriters",
        "uk.ac.kcl.cleanup"})
@EnableBatchProcessing
@Import({
        BatchConfigurer.class,
        RemoteConfiguration.class,
        LocalConfiguration.class
})
public class JobConfiguration {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JobConfiguration.class);

    @StepScope





    @Bean
    public LoggerHelper loggerHelper(){
        LoggerHelper lh = new LoggerHelper();
        lh.setContextID(env.getProperty("job.jobName"));
        return lh;
    }

    /*
        
    
    *******************************************COMMON BEANS
        
    
    */
    //required to process placeholder values in annotations, e.g. scheduler cron
    @Bean
    public static PropertySourcesPlaceholderConfigurer placeHolderConfigurer() {
        PropertySourcesPlaceholderConfigurer props = new PropertySourcesPlaceholderConfigurer();
        props.setNullValue("");
        return props;
    }

    @Autowired
    public Environment env;

    @Value("${step.concurrencyLimit:1}")
    int concurrencyLimit;

    @Value("${step.chunkSize:50}")
    int chunkSize;

    @Value("${step.skipLimit:5}")
    int skipLimit;

    @Bean
    @Qualifier("slaveTaskExecutor")
    public TaskExecutor taskExecutor() {
//        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
//        exec.setMaxPoolSize(Integer.parseInt(env.getProperty("concurrencyLimit")));
        SimpleAsyncTaskExecutor exec = new SimpleAsyncTaskExecutor();
        exec.setConcurrencyLimit(concurrencyLimit);
        return exec;
    }



    @Bean(destroyMethod = "close")
    @Primary
    @Qualifier("sourceDataSource")
    //@Scope("prototype")
    public DataSource sourceDataSource() {
        HikariDataSource mainDatasource = new HikariDataSource();
        executeSessionScripts(mainDatasource,env.getProperty("source.Driver"));
        mainDatasource.setDriverClassName(env.getProperty("source.Driver"));
        mainDatasource.setJdbcUrl(env.getProperty("source.JdbcPath"));
        mainDatasource.setUsername(env.getProperty("source.username"));
        mainDatasource.setPassword(env.getProperty("source.password"));
        mainDatasource.setIdleTimeout(Long.valueOf(env.getProperty("source.idleTimeout")));
        mainDatasource.setMaxLifetime(Long.valueOf(env.getProperty("source.maxLifetime")));
        //mainDatasource.setConnectionTestQuery("select 1");

        return mainDatasource;
    }


    @Bean(destroyMethod = "close")
    //@Scope("prototype")
    @Qualifier("targetDataSource")
    public DataSource targetDataSource() {
        HikariDataSource mainDatasource = new HikariDataSource();
        executeSessionScripts(mainDatasource,env.getProperty("target.Driver"));
        mainDatasource.setDriverClassName(env.getProperty("target.Driver"));
        mainDatasource.setJdbcUrl(env.getProperty("target.JdbcPath"));
        mainDatasource.setUsername(env.getProperty("target.username"));
        mainDatasource.setPassword(env.getProperty("target.password"));
        mainDatasource.setIdleTimeout(Long.valueOf(env.getProperty("target.idleTimeout")));
        mainDatasource.setMaxLifetime(Long.valueOf(env.getProperty("target.maxLifetime")));
        //mainDatasource.setConnectionTestQuery("select 1");
        //?? should be managed by transaction manager
        //mainDatasource.setAutoCommit(false);
        return mainDatasource;

    }

//    @Bean
//    @Profile("jdbc")
//    @Qualifier("targetDatasourceTransactionManager")
//    public PlatformTransactionManager targetDatasourceTransactionManager(
//            @Qualifier("targetDataSource")
//                    DataSource jdbcDocumentTarget) {
//        DataSourceTransactionManager tx = new DataSourceTransactionManager();
//        tx.setDataSource(jdbcDocumentTarget);
//        return tx;
//    }





    private void executeSessionScripts(HikariDataSource mainDatasource, String driver) {
        //temp datasource required to get type
        DatabaseType type = null;

            switch (driver) {
                case "DERBY":
                    break;
                case "DB2":
                    break;
                case "DB2ZOS":
                    break;
                case "HSQL":
                    break;
                case "com.microsoft.sqlserver.jdbc.SQLServerDriver":
                    mainDatasource.setConnectionInitSql("SET DATEFORMAT ymd;");
                    break;
                case "MYSQL":
                    break;
                case "ORACLE":
                    break;
                case "POSTGRES":
                    break;
                case "SYBASE":
                    break;
                case "H2":
                    break;
                case "SQLITE":
                    break;
            }

    }

    @Value("${target.username}")
    String targetUserName;
    @Value("${target.password}")
    String targetPassword;

    @Bean
    public BeanFactoryStepLocator stepLocator(){
        return new BeanFactoryStepLocator();
    }





    @Bean
    public StepExecutionRequestHandler stepExecutionRequestHandler(
            JobExplorer jobExplorer, BeanFactoryStepLocator stepLocator){
        StepExecutionRequestHandler handler = new StepExecutionRequestHandler();
        handler.setJobExplorer(jobExplorer);
        handler.setStepLocator(stepLocator);
        return handler;
    }

    @Bean
    @Qualifier("jsonMakerItemProcessor")
    public JSONMakerItemProcessor jsonMakerItemProcessor(){
        return new JSONMakerItemProcessor();
    }

    @Bean
    @Qualifier("runIdIncrementer")
    public RunIdIncrementer runIdIncrementer(){return new RunIdIncrementer();}




    @Bean
    @Qualifier("compositeSlaveStep")
    public Step compositeSlaveStep(
                        ItemReader<Document> reader,
            @Qualifier("compositeItemProcessor") ItemProcessor<Document, Document> processor,
            @Qualifier("compositeESandJdbcItemWriter") ItemWriter<Document> writer,
            @Qualifier("slaveTaskExecutor")TaskExecutor taskExecutor,
            @Qualifier("nonFatalExceptionItemProcessorListener")
                                ItemProcessListener nonFatalExceptionItemProcessorListener,
            //@Qualifier("targetDatasourceTransactionManager")PlatformTransactionManager manager,
            StepBuilderFactory stepBuilderFactory
    ) {
        return stepBuilderFactory.get("compositeSlaveStep")
                .<Document, Document> chunk(chunkSize)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(skipLimit)
                .skip(WebserviceProcessingFailedException.class)
                .noSkip(Exception.class)
         //       .listener(nonFatalExceptionItemProcessorListener)
                .taskExecutor(taskExecutor)
                .build();
    }

    @Autowired
    StepPartitioner stepPartitioner;

    @Bean
    @StepScope
    @Qualifier("documentItemReader")
    @Profile("jdbc_in")
    public ItemReader<Document> documentItemReader(
            @Value("#{stepExecutionContext[minValue]}") String minValue,
            @Value("#{stepExecutionContext[maxValue]}") String maxValue,
            @Value("#{stepExecutionContext[min_time_stamp]}") String minTimeStamp,
            @Value("#{stepExecutionContext[max_time_stamp]}") String maxTimeStamp,
            @Qualifier("documentRowMapper")RowMapper<Document> documentRowmapper,
            @Qualifier("sourceDataSource") DataSource jdbcDocumentSource) throws Exception {

        JdbcPagingItemReader<Document> reader = new JdbcPagingItemReader<>();
        reader.setDataSource(jdbcDocumentSource);
        SqlPagingQueryProviderFactoryBean qp = new SqlPagingQueryProviderFactoryBean();
        qp.setSelectClause(env.getProperty("source.selectClause"));
        qp.setFromClause(env.getProperty("source.fromClause"));
        qp.setSortKey(env.getProperty("source.sortKey"));
        qp.setWhereClause(stepPartitioner.getPartitioningLogic(minValue,maxValue, minTimeStamp,maxTimeStamp));
        qp.setDataSource(jdbcDocumentSource);
        reader.setPageSize(Integer.parseInt(env.getProperty("source.pageSize")));
        reader.setQueryProvider(qp.getObject());
        reader.setRowMapper(documentRowmapper);
        return reader;
    }

    @Bean
    @StepScope
    @Qualifier("simpleJdbcItemWriter")
    @Profile("jdbc_out")
    public ItemWriter<Document> simpleJdbcItemWriter(
            @Qualifier("targetDataSource") DataSource jdbcDocumentTarget) {
        JdbcBatchItemWriter<Document> writer = new JdbcBatchItemWriter<>();
        writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());
        writer.setSql(env.getProperty("target.Sql"));
        writer.setDataSource(jdbcDocumentTarget);
        return writer;
    }

    @Autowired(required = false)
    @Qualifier("esDocumentWriter")
    ItemWriter<Document> esItemWriter;

    @Autowired(required = false)
    @Qualifier("simpleJdbcItemWriter")
    ItemWriter<Document> jdbcItemWriter;

    @Autowired(required = false)
    @Qualifier("jsonFileItemWriter")
    ItemWriter<Document> jsonFileItemWriter;

    @Autowired(required = false)
    @Qualifier("pdfFileItemWriter")
    ItemWriter<Document> pdfFileItemWriter;

    @Autowired(required = false)
    @Qualifier("thumbnailFileItemWriter")
    ItemWriter<Document> thumbnailFileItemWriter;

    @Bean
    @Qualifier("compositeItemWriter")
    public ItemWriter<Document> compositeESandJdbcItemWriter() {
        CompositeItemWriter writer = new CompositeItemWriter<>();
        ArrayList<ItemWriter<Document>> delegates = new ArrayList<>();
        if(esItemWriter !=null) delegates.add(esItemWriter);
        if(jdbcItemWriter !=null) delegates.add(jdbcItemWriter);
        if(jsonFileItemWriter !=null) delegates.add(jsonFileItemWriter);
        if(pdfFileItemWriter != null) delegates.add(pdfFileItemWriter);
        if(thumbnailFileItemWriter !=null) delegates.add(thumbnailFileItemWriter);
        writer.setDelegates(delegates);
        return writer;
    }



    @Autowired(required = false)
    @Qualifier("gateDocumentItemProcessor")
    ItemProcessor<Document, Document> gateItemProcessor;

    @Autowired(required = false)
    @Qualifier("dBLineFixerItemProcessor")
    ItemProcessor<Document, Document> dBLineFixerItemProcessor;

    @Autowired(required = false)
    @Qualifier("tikaDocumentItemProcessor")
    ItemProcessor<Document, Document> tikaItemProcessor;

    @Autowired(required = false)
    @Qualifier("metadataItemProcessor")
    ItemProcessor<Document, Document> metadataItemProcessor;

    @Autowired(required = false)
    @Qualifier("deIdDocumentItemProcessor")
    ItemProcessor<Document, Document> deIdDocumentItemProcessor;

    @Autowired(required = false)
    @Qualifier("webserviceDocumentItemProcessor")
    ItemProcessor<Document, Document> webserviceDocumentItemProcessor;

    @Autowired
    @Qualifier("jsonMakerItemProcessor")
    ItemProcessor<Document, Document> jsonMakerItemProcessor;



    @Bean
    @Qualifier("compositeItemProcessorr")
    public ItemProcessor<Document,Document> compositeItemProcessor() {
        CompositeItemProcessor processor = new CompositeItemProcessor<>();
        ArrayList<ItemProcessor<Document,Document>> delegates = new ArrayList<>();

        if(tikaItemProcessor !=null) delegates.add(tikaItemProcessor);
        if(metadataItemProcessor !=null) delegates.add(metadataItemProcessor);
        if(dBLineFixerItemProcessor !=null) delegates.add(dBLineFixerItemProcessor);
        if(gateItemProcessor !=null) delegates.add(gateItemProcessor);
        if(deIdDocumentItemProcessor !=null) delegates.add(deIdDocumentItemProcessor);
        if(webserviceDocumentItemProcessor !=null) delegates.add(webserviceDocumentItemProcessor);

        delegates.add(jsonMakerItemProcessor);
        processor.setDelegates(delegates);
        return processor;
    }

}
