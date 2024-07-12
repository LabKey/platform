package org.labkey.api.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.CacheProvider;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.databind.cfg.DatatypeFeature;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.cfg.MutableCoercionConfig;
import com.fasterxml.jackson.databind.cfg.MutableConfigOverride;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.introspect.AccessorNamingStrategy;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.SubtypeResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

@SuppressWarnings("deprecation")
class LabKeyObjectMapper extends ObjectMapper
{
    final boolean locked;

    LabKeyObjectMapper(boolean locked)
    {
        super(JsonUtil.createDefaultJsonFactoryBuilder().build());
        // Allow org.json classes to be serialized by Jackson
        super.registerModule(new JsonOrgModule());
        // We must register JavaTimeModule in order to serialize LocalDate, etc.
        super.registerModule(new JavaTimeModule());
        super.setDateFormat(new SimpleDateFormat(DateUtil.getJsonDateTimeFormatString()));
        this.locked = locked;
    }

    LabKeyObjectMapper(LabKeyObjectMapper src)
    {
        this(src, null);
    }

    LabKeyObjectMapper(LabKeyObjectMapper src, JsonFactory factory)
    {
        super(src, factory);
        locked = false;
    }

    @Override
    public ObjectMapper copy()
    {
        _checkInvalidCopy(LabKeyObjectMapper.class);
        return new LabKeyObjectMapper(this);
    }

    @Override
    public ObjectMapper copyWith(JsonFactory factory)
    {
        _checkInvalidCopy(LabKeyObjectMapper.class);
        return new LabKeyObjectMapper(this, factory);
    }

    /*
     * We need to lock the configuration of the shared ObjectMapper.
     * There are a LOT of methods, so we'll check "locked" for most methods.
     */

    private void checkLocked()
    {
        if (locked)
            throw new IllegalStateException("Shouldn't be changing the configuration of JsonUtil.DEFAULT_MAPPER");
    }


    @Override
    protected ClassIntrospector defaultClassIntrospector()
    {
        checkLocked();
        return super.defaultClassIntrospector();
    }

    @Override
    public ObjectMapper registerModule(Module module)
    {
        checkLocked();
        return super.registerModule(module);
    }

    @Override
    public ObjectMapper registerModules(Module... modules)
    {
        checkLocked();
        return super.registerModules(modules);
    }

    @Override
    public ObjectMapper registerModules(Iterable<? extends Module> modules)
    {
        checkLocked();
        return super.registerModules(modules);
    }

    @Override
    public Set<Object> getRegisteredModuleIds()
    {
        checkLocked();
        return super.getRegisteredModuleIds();
    }

    @Override
    public ObjectMapper findAndRegisterModules()
    {
        checkLocked();
        return super.findAndRegisterModules();
    }

    @Override
    public DeserializationContext getDeserializationContext()
    {
        checkLocked();
        return super.getDeserializationContext();
    }

    @Override
    public ObjectMapper setSerializerFactory(SerializerFactory f)
    {
        checkLocked();
        return super.setSerializerFactory(f);
    }

    @Override
    public SerializerFactory getSerializerFactory()
    {
        checkLocked();
        return super.getSerializerFactory();
    }

    @Override
    public ObjectMapper setSerializerProvider(DefaultSerializerProvider p)
    {
        checkLocked();
        return super.setSerializerProvider(p);
    }

    @Override
    public SerializerProvider getSerializerProvider()
    {
        checkLocked();
        return super.getSerializerProvider();
    }

    @Override
    public SerializerProvider getSerializerProviderInstance()
    {
        checkLocked();
        return super.getSerializerProviderInstance();
    }

    @Override
    public ObjectMapper setMixIns(Map<Class<?>, Class<?>> sourceMixins)
    {
        checkLocked();
        return super.setMixIns(sourceMixins);
    }

    @Override
    public ObjectMapper addMixIn(Class<?> target, Class<?> mixinSource)
    {
        checkLocked();
        return super.addMixIn(target, mixinSource);
    }

    @Override
    public ObjectMapper setMixInResolver(ClassIntrospector.MixInResolver resolver)
    {
        checkLocked();
        return super.setMixInResolver(resolver);
    }

    @Override
    public Class<?> findMixInClassFor(Class<?> cls)
    {
        checkLocked();
        return super.findMixInClassFor(cls);
    }

    @Override
    public int mixInCount()
    {
        checkLocked();
        return super.mixInCount();
    }

    @Override
    public void setMixInAnnotations(Map<Class<?>, Class<?>> sourceMixins)
    {
        checkLocked();
        super.setMixInAnnotations(sourceMixins);
    }

    @Override
    public VisibilityChecker<?> getVisibilityChecker()
    {
        checkLocked();
        return super.getVisibilityChecker();
    }

    @Override
    public ObjectMapper setVisibility(VisibilityChecker<?> vc)
    {
        checkLocked();
        return super.setVisibility(vc);
    }

    @Override
    public ObjectMapper setVisibility(PropertyAccessor forMethod, JsonAutoDetect.Visibility visibility)
    {
        checkLocked();
        return super.setVisibility(forMethod, visibility);
    }

    @Override
    public SubtypeResolver getSubtypeResolver()
    {
        checkLocked();
        return super.getSubtypeResolver();
    }

    @Override
    public ObjectMapper setSubtypeResolver(SubtypeResolver str)
    {
        checkLocked();
        return super.setSubtypeResolver(str);
    }

    @Override
    public ObjectMapper setAnnotationIntrospector(AnnotationIntrospector ai)
    {
        checkLocked();
        return super.setAnnotationIntrospector(ai);
    }

    @Override
    public ObjectMapper setAnnotationIntrospectors(AnnotationIntrospector serializerAI, AnnotationIntrospector deserializerAI)
    {
        checkLocked();
        return super.setAnnotationIntrospectors(serializerAI, deserializerAI);
    }

    @Override
    public ObjectMapper setPropertyNamingStrategy(PropertyNamingStrategy s)
    {
        checkLocked();
        return super.setPropertyNamingStrategy(s);
    }

    @Override
    public PropertyNamingStrategy getPropertyNamingStrategy()
    {
        checkLocked();
        return super.getPropertyNamingStrategy();
    }

    @Override
    public ObjectMapper setAccessorNaming(AccessorNamingStrategy.Provider s)
    {
        checkLocked();
        return super.setAccessorNaming(s);
    }

    @Override
    public ObjectMapper setDefaultPrettyPrinter(PrettyPrinter pp)
    {
        checkLocked();
        return super.setDefaultPrettyPrinter(pp);
    }

    @Override
    public void setVisibilityChecker(VisibilityChecker<?> vc)
    {
        checkLocked();
        super.setVisibilityChecker(vc);
    }

    @Override
    public ObjectMapper setPolymorphicTypeValidator(PolymorphicTypeValidator ptv)
    {
        checkLocked();
        return super.setPolymorphicTypeValidator(ptv);
    }

    @Override
    public PolymorphicTypeValidator getPolymorphicTypeValidator()
    {
        checkLocked();
        return super.getPolymorphicTypeValidator();
    }

    @Override
    public ObjectMapper setSerializationInclusion(JsonInclude.Include incl)
    {
        checkLocked();
        return super.setSerializationInclusion(incl);
    }

    @Override
    public ObjectMapper setPropertyInclusion(JsonInclude.Value incl)
    {
        checkLocked();
        return super.setPropertyInclusion(incl);
    }

    @Override
    public ObjectMapper setDefaultPropertyInclusion(JsonInclude.Value incl)
    {
        checkLocked();
        return super.setDefaultPropertyInclusion(incl);
    }

    @Override
    public ObjectMapper setDefaultPropertyInclusion(JsonInclude.Include incl)
    {
        checkLocked();
        return super.setDefaultPropertyInclusion(incl);
    }

    @Override
    public ObjectMapper setDefaultSetterInfo(JsonSetter.Value v)
    {
        checkLocked();
        return super.setDefaultSetterInfo(v);
    }

    @Override
    public ObjectMapper setDefaultVisibility(JsonAutoDetect.Value vis)
    {
        checkLocked();
        return super.setDefaultVisibility(vis);
    }

    @Override
    public ObjectMapper setDefaultMergeable(Boolean b)
    {
        checkLocked();
        return super.setDefaultMergeable(b);
    }

    @Override
    public ObjectMapper setDefaultLeniency(Boolean b)
    {
        checkLocked();
        return super.setDefaultLeniency(b);
    }

    @Override
    public void registerSubtypes(Class<?>... classes)
    {
        checkLocked();
        super.registerSubtypes(classes);
    }

    @Override
    public void registerSubtypes(NamedType... types)
    {
        checkLocked();
        super.registerSubtypes(types);
    }

    @Override
    public void registerSubtypes(Collection<Class<?>> subtypes)
    {
        checkLocked();
        super.registerSubtypes(subtypes);
    }

    @Override
    public ObjectMapper activateDefaultTyping(PolymorphicTypeValidator ptv)
    {
        checkLocked();
        return super.activateDefaultTyping(ptv);
    }

    @Override
    public ObjectMapper activateDefaultTyping(PolymorphicTypeValidator ptv, DefaultTyping applicability)
    {
        checkLocked();
        return super.activateDefaultTyping(ptv, applicability);
    }

    @Override
    public ObjectMapper activateDefaultTyping(PolymorphicTypeValidator ptv, DefaultTyping applicability, JsonTypeInfo.As includeAs)
    {
        checkLocked();
        return super.activateDefaultTyping(ptv, applicability, includeAs);
    }

    @Override
    public ObjectMapper activateDefaultTypingAsProperty(PolymorphicTypeValidator ptv, DefaultTyping applicability, String propertyName)
    {
        checkLocked();
        return super.activateDefaultTypingAsProperty(ptv, applicability, propertyName);
    }

    @Override
    public ObjectMapper deactivateDefaultTyping()
    {
        checkLocked();
        return super.deactivateDefaultTyping();
    }

    @Override
    public ObjectMapper setDefaultTyping(TypeResolverBuilder<?> typer)
    {
        checkLocked();
        return super.setDefaultTyping(typer);
    }

    @Override
    public ObjectMapper enableDefaultTyping()
    {
        checkLocked();
        return super.enableDefaultTyping();
    }

    @Override
    public ObjectMapper enableDefaultTyping(DefaultTyping dti)
    {
        checkLocked();
        return super.enableDefaultTyping(dti);
    }

    @Override
    public ObjectMapper enableDefaultTyping(DefaultTyping applicability, JsonTypeInfo.As includeAs)
    {
        checkLocked();
        return super.enableDefaultTyping(applicability, includeAs);
    }

    @Override
    public ObjectMapper enableDefaultTypingAsProperty(DefaultTyping applicability, String propertyName)
    {
        checkLocked();
        return super.enableDefaultTypingAsProperty(applicability, propertyName);
    }

    @Override
    public ObjectMapper disableDefaultTyping()
    {
        checkLocked();
        return super.disableDefaultTyping();
    }

    @Override
    public MutableConfigOverride configOverride(Class<?> type)
    {
        checkLocked();
        return super.configOverride(type);
    }

    @Override
    public MutableCoercionConfig coercionConfigDefaults()
    {
        checkLocked();
        return super.coercionConfigDefaults();
    }

    @Override
    public MutableCoercionConfig coercionConfigFor(LogicalType logicalType)
    {
        checkLocked();
        return super.coercionConfigFor(logicalType);
    }

    @Override
    public MutableCoercionConfig coercionConfigFor(Class<?> physicalType)
    {
        checkLocked();
        return super.coercionConfigFor(physicalType);
    }

    @Override
    public TypeFactory getTypeFactory()
    {
        checkLocked();
        return super.getTypeFactory();
    }

    @Override
    public ObjectMapper setTypeFactory(TypeFactory f)
    {
        checkLocked();
        return super.setTypeFactory(f);
    }

    @Override
    public JsonNodeFactory getNodeFactory()
    {
        checkLocked();
        return super.getNodeFactory();
    }

    @Override
    public ObjectMapper setNodeFactory(JsonNodeFactory f)
    {
        checkLocked();
        return super.setNodeFactory(f);
    }

    @Override
    public ObjectMapper setConstructorDetector(ConstructorDetector cd)
    {
        checkLocked();
        return super.setConstructorDetector(cd);
    }

    @Override
    public ObjectMapper setCacheProvider(CacheProvider cacheProvider)
    {
        checkLocked();
        return super.setCacheProvider(cacheProvider);
    }

    @Override
    public ObjectMapper addHandler(DeserializationProblemHandler h)
    {
        checkLocked();
        return super.addHandler(h);
    }

    @Override
    public ObjectMapper clearProblemHandlers()
    {
        checkLocked();
        return super.clearProblemHandlers();
    }

    @Override
    public ObjectMapper setConfig(DeserializationConfig config)
    {
        checkLocked();
        return super.setConfig(config);
    }

    @Override
    public void setFilters(FilterProvider filterProvider)
    {
        checkLocked();
        super.setFilters(filterProvider);
    }

    @Override
    public ObjectMapper setFilterProvider(FilterProvider filterProvider)
    {
        checkLocked();
        return super.setFilterProvider(filterProvider);
    }

    @Override
    public ObjectMapper setBase64Variant(Base64Variant v)
    {
        checkLocked();
        return super.setBase64Variant(v);
    }

    @Override
    public ObjectMapper setConfig(SerializationConfig config)
    {
        checkLocked();
        return super.setConfig(config);
    }

    @Override
    public JsonFactory tokenStreamFactory()
    {
        checkLocked();
        return super.tokenStreamFactory();
    }

    @Override
    public JsonFactory getFactory()
    {
        checkLocked();
        return super.getFactory();
    }

    @Override
    public ObjectMapper setDateFormat(DateFormat dateFormat)
    {
        checkLocked();
        return super.setDateFormat(dateFormat);
    }

    @Override
    public DateFormat getDateFormat()
    {
        checkLocked();
        return super.getDateFormat();
    }

    @Override
    public Object setHandlerInstantiator(HandlerInstantiator hi)
    {
        checkLocked();
        return super.setHandlerInstantiator(hi);
    }

    @Override
    public ObjectMapper setInjectableValues(InjectableValues injectableValues)
    {
        checkLocked();
        return super.setInjectableValues(injectableValues);
    }

    @Override
    public InjectableValues getInjectableValues()
    {
        checkLocked();
        return super.getInjectableValues();
    }

    @Override
    public ObjectMapper setLocale(Locale l)
    {
        checkLocked();
        return super.setLocale(l);
    }

    @Override
    public ObjectMapper setTimeZone(TimeZone tz)
    {
        checkLocked();
        return super.setTimeZone(tz);
    }

    @Override
    public ObjectMapper setDefaultAttributes(ContextAttributes attrs)
    {
        checkLocked();
        return super.setDefaultAttributes(attrs);
    }

    @Override
    public boolean isEnabled(MapperFeature f)
    {
        checkLocked();
        return super.isEnabled(f);
    }

    @Override
    public ObjectMapper configure(MapperFeature f, boolean state)
    {
        checkLocked();
        return super.configure(f, state);
    }

    @Override
    public ObjectMapper enable(MapperFeature... f)
    {
        checkLocked();
        return super.enable(f);
    }

    @Override
    public ObjectMapper disable(MapperFeature... f)
    {
        checkLocked();
        return super.disable(f);
    }

    @Override
    public boolean isEnabled(SerializationFeature f)
    {
        checkLocked();
        return super.isEnabled(f);
    }

    @Override
    public ObjectMapper configure(SerializationFeature f, boolean state)
    {
        checkLocked();
        return super.configure(f, state);
    }

    @Override
    public ObjectMapper enable(SerializationFeature f)
    {
        checkLocked();
        return super.enable(f);
    }

    @Override
    public ObjectMapper enable(SerializationFeature first, SerializationFeature... f)
    {
        checkLocked();
        return super.enable(first, f);
    }

    @Override
    public ObjectMapper disable(SerializationFeature f)
    {
        checkLocked();
        return super.disable(f);
    }

    @Override
    public ObjectMapper disable(SerializationFeature first, SerializationFeature... f)
    {
        checkLocked();
        return super.disable(first, f);
    }

    @Override
    public ObjectMapper configure(DeserializationFeature f, boolean state)
    {
        checkLocked();
        return super.configure(f, state);
    }

    @Override
    public ObjectMapper enable(DeserializationFeature feature)
    {
        checkLocked();
        return super.enable(feature);
    }

    @Override
    public ObjectMapper enable(DeserializationFeature first, DeserializationFeature... f)
    {
        checkLocked();
        return super.enable(first, f);
    }

    @Override
    public ObjectMapper disable(DeserializationFeature feature)
    {
        checkLocked();
        return super.disable(feature);
    }

    @Override
    public ObjectMapper disable(DeserializationFeature first, DeserializationFeature... f)
    {
        checkLocked();
        return super.disable(first, f);
    }

    @Override
    public ObjectMapper configure(DatatypeFeature f, boolean state)
    {
        checkLocked();
        return super.configure(f, state);
    }

    @Override
    public boolean isEnabled(JsonParser.Feature f)
    {
        checkLocked();
        return super.isEnabled(f);
    }

    @Override
    public ObjectMapper configure(JsonParser.Feature f, boolean state)
    {
        checkLocked();
        return super.configure(f, state);
    }

    @Override
    public ObjectMapper enable(JsonParser.Feature... features)
    {
        checkLocked();
        return super.enable(features);
    }

    @Override
    public ObjectMapper disable(JsonParser.Feature... features)
    {
        checkLocked();
        return super.disable(features);
    }

    @Override
    public boolean isEnabled(JsonGenerator.Feature f)
    {
        checkLocked();
        return super.isEnabled(f);
    }

    @Override
    public ObjectMapper configure(JsonGenerator.Feature f, boolean state)
    {
        checkLocked();
        return super.configure(f, state);
    }

    @Override
    public ObjectMapper enable(JsonGenerator.Feature... features)
    {
        checkLocked();
        return super.enable(features);
    }

    @Override
    public ObjectMapper disable(JsonGenerator.Feature... features)
    {
        checkLocked();
        return super.disable(features);
    }

    @Override
    public void acceptJsonFormatVisitor(Class<?> type, JsonFormatVisitorWrapper visitor) throws JsonMappingException
    {
        checkLocked();
        super.acceptJsonFormatVisitor(type, visitor);
    }

    @Override
    public void acceptJsonFormatVisitor(JavaType type, JsonFormatVisitorWrapper visitor) throws JsonMappingException
    {
        checkLocked();
        super.acceptJsonFormatVisitor(type, visitor);
    }

    @Override
    public JsonFactory getJsonFactory()
    {
        checkLocked();
        return super.getJsonFactory();
    }
}
