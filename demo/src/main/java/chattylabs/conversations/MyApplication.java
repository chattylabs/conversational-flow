package chattylabs.conversations;

import com.chattylabs.android.commons.internal.ILogger;
import com.chattylabs.android.commons.internal.ILoggerImpl;
import chattylabs.conversations.demo.BuildFromJsonActivity;

import chattylabs.conversations.demo.TestingAddonsActivity;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;
import dagger.android.support.AndroidSupportInjectionModule;
import dagger.android.support.DaggerApplication;

public class MyApplication extends DaggerApplication {

    @dagger.Component(
            modules = {
                    AndroidSupportInjectionModule.class,
                    MyApplicationModule.class
            }
    )
    /* @ApplicationScoped and/or @Singleton */
    interface Component extends AndroidInjector<MyApplication> {
        @dagger.Component.Builder
        abstract class Builder extends AndroidInjector.Builder<MyApplication> {}
    }

    @dagger.Module
    static abstract class MyApplicationModule {

        @dagger.Provides
        @dagger.Reusable
        public static ConversationalFlow provideConversationalFlow(ILogger logger) {
            return ConversationalFlow.provide(logger);
        }

        @dagger.Binds
        @dagger.Reusable
        abstract ILogger provideLogger(ILoggerImpl logger);

        @ContributesAndroidInjector
        abstract TestingAddonsActivity injectorActivity_1();

        @ContributesAndroidInjector
        abstract BuildFromJsonActivity injectorActivity_2();
    }

    @Override
    protected AndroidInjector<MyApplication> applicationInjector() {
        return DaggerMyApplication_Component.builder().create(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
