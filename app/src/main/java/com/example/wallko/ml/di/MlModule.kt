package com.example.wallko.ml.di

import com.example.wallko.ml.classifier.AiyPlantClassifier
import com.example.wallko.ml.classifier.PlantClassifier
import com.example.wallko.ml.segment.MlKitSubjectSegmenter
import com.example.wallko.ml.segment.SubjectSegmenter
import com.example.wallko.ml.stylize.MagentaStylizer
import com.example.wallko.ml.stylize.Stylizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {
    @Binds
    @Singleton
    abstract fun bindPlantClassifier(impl: AiyPlantClassifier): PlantClassifier

    @Binds
    @Singleton
    abstract fun bindSubjectSegmenter(impl: MlKitSubjectSegmenter): SubjectSegmenter

    @Binds
    @Singleton
    abstract fun bindStylizer(impl: MagentaStylizer): Stylizer
}
