package com.rxhttp.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import rxhttp.wrapper.annotation.Converter
import rxhttp.wrapper.annotation.DefaultDomain
import rxhttp.wrapper.annotation.Domain
import rxhttp.wrapper.annotation.OkClient
import rxhttp.wrapper.annotation.Param
import rxhttp.wrapper.annotation.Parser

lateinit var rxHttpPackage: String //RxHttp相关类的包名


/**
 * User: ljx
 * Date: 2021/10/8
 * Time: 16:31
 * 现存在问题：
 * 1、Parser注解里的wrappers字段还未适配，需要等ksp修复bug
 * 2、无法识别到方法抛出的异常，需要等ksp适配
 *
 *
 */
class RxHttpProcessor(
    val options: Map<String, String>,
    val kotlinVersion: KotlinVersion,
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {

    companion object {
        private const val TAG = "KspProcessor"
        const val rxhttp_rxjava = "rxhttp_rxjava"
        const val rxhttp_package = "rxhttp_package"
        const val rxhttp_incremental = "rxhttp_incremental"
        const val rxhttp_debug = "rxhttp_debug"
    }

    private var called: Boolean = false

    private val rxHttpGenerator = RxHttpGenerator()


    @OptIn(
        KspExperimental::class,
        KotlinPoetJavaPoetPreview::class,
        KotlinPoetKspPreview::class
    )
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (called) {
            return emptyList()
        }
        val map = options
        rxHttpPackage = map[rxhttp_package] ?: "com.example.httpsender"
        val incremental = "false" != map[rxhttp_incremental]
        val debug = "true" == map[rxhttp_debug]
        initRxJavaVersion(map[rxhttp_rxjava])

        val rxHttpWrapper = RxHttpWrapper(logger)

        val domainVisitor = DomainVisitor(logger)
        resolver.getSymbolsWithAnnotation(Domain::class.java.name).forEach {
            if (it is KSPropertyDeclaration && it.validate()) {
                it.accept(domainVisitor, Unit)
                rxHttpWrapper.addDomain(it)
            }
        }

        val defaultDomainVisitor = DefaultDomainVisitor(logger)
        resolver.getSymbolsWithAnnotation(DefaultDomain::class.java.name).forEach {
            if (it is KSPropertyDeclaration && it.validate()) {
                it.accept(defaultDomainVisitor, Unit)
            }
        }

        val okClientVisitor = OkClientVisitor(logger)
        resolver.getSymbolsWithAnnotation(OkClient::class.java.name).forEach {
            if (it is KSPropertyDeclaration && it.validate()) {
                it.accept(okClientVisitor, Unit)
                rxHttpWrapper.addOkClient(it)
            }
        }

        val converterVisitor = ConverterVisitor(logger)
        resolver.getSymbolsWithAnnotation(Converter::class.java.name).forEach {
            if (it is KSPropertyDeclaration && it.validate()) {
                it.accept(converterVisitor, Unit)
                rxHttpWrapper.addConverter(it)
            }
        }

        val parserVisitor = ParserVisitor(logger)
        resolver.getSymbolsWithAnnotation(Parser::class.java.name).forEach {
            if (it is KSClassDeclaration && it.validate()) {
                it.accept(parserVisitor, Unit)
            }
        }

        val paramsVisitor = ParamsVisitor(logger, resolver)
        resolver.getSymbolsWithAnnotation(Param::class.java.name).forEach {
            if (it is KSClassDeclaration && it.validate()) {
                it.accept(paramsVisitor, Unit)
                rxHttpWrapper.add(it)
            }
        }
        rxHttpGenerator.paramsVisitor = paramsVisitor
        rxHttpGenerator.parserVisitor = parserVisitor
        rxHttpGenerator.domainVisitor = domainVisitor
        rxHttpGenerator.okClientVisitor = okClientVisitor
        rxHttpGenerator.converterVisitor = converterVisitor
        rxHttpGenerator.defaultDomainVisitor = defaultDomainVisitor
        rxHttpGenerator.generateCode(codeGenerator)
        ClassHelper.generatorStaticClass(codeGenerator, true)
        rxHttpWrapper.generateRxWrapper(codeGenerator)
        called = true
        return emptyList()
    }

    override fun finish() {}

    override fun onError() {

    }


    class Provider : SymbolProcessorProvider {

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return RxHttpProcessor(
                environment.options, environment.kotlinVersion,
                environment.codeGenerator, environment.logger
            )
        }
    }
}