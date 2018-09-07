package com.chattylabs.demo.voice;

import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.common.internal.ILoggerImpl;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent;
import com.chattylabs.sdk.android.voice.ConversationalFlowModule;

import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;
import dagger.android.support.AndroidSupportInjectionModule;
import dagger.android.support.DaggerApplication;

public class DemoApplication extends DaggerApplication {

    @dagger.Component(
            modules = {
                    AndroidSupportInjectionModule.class,
                    DemoModule.class
            }
    )
    /* @ApplicationScoped and/or @Singleton */
    interface Component extends AndroidInjector<DemoApplication> {
        @dagger.Component.Builder
        abstract class Builder extends AndroidInjector.Builder<DemoApplication> {}
    }

    @dagger.Module
    static abstract class DemoModule {

        @dagger.Provides
        @dagger.Reusable
        public static ConversationalFlowComponent provideComponent(ILogger logger) {
            return ConversationalFlowModule.provideComponent(logger);
        }

        @dagger.Binds
        @dagger.Reusable
        abstract ILogger provideLogger(ILoggerImpl logger);

        @ContributesAndroidInjector
        abstract TestTheComponentActivity conversationCreatorActivity();

        @ContributesAndroidInjector
        abstract BuildFromJsonActivity mainActivity();
    }

    @Override
    protected AndroidInjector<DemoApplication> applicationInjector() {
        return DaggerDemoApplication_Component.builder().create(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
