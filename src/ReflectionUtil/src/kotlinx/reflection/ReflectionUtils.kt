package kotlinx.reflection

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.load.java.reflect.tryLoadClass
import java.util.*
import java.lang.reflect.*
import kotlin.Array
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addIfNotNull
import kotlin.reflect.KClass
import kotlin.reflect.KMemberProperty
import kotlin.reflect.jvm.*
import kotlin.reflect.jvm.internal.DescriptorBasedProperty
import kotlin.reflect.jvm.internal.KClassImpl
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

object ReflectionCache {
    val objects = ConcurrentHashMap<Class<*>, Any>()
    val companionObjects = ConcurrentHashMap<Class<*>, Any>()
    val consMetadata = ConcurrentHashMap<Class<*>, Triple<Constructor<*>, Array<Class<*>>, List<ValueParameterDescriptor>>>()
    val primaryProperites = ConcurrentHashMap<Class<*>, List<String>>()
    val propertyGetters = ConcurrentHashMap<Pair<KClass<*>, String>, KMemberProperty<Any, Any?>?>()
}

private object NullMask
private fun Any.unmask():Any? = if (this == NullMask) null else this

fun Class<*>.objectInstance0(): Any? {
    return ReflectionCache.objects.getOrPut(this) {
        try {
            val field = getDeclaredField("INSTANCE\$")
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
                if (!field.isAccessible()) {
                    field.setAccessible(true)
                }
                field[null]!!
            }
            else NullMask
        }
        catch (e: NoSuchFieldException) {
            NullMask
        }
    }.unmask()
}

fun Class<*>.objectInstance(): Any? {
    return ReflectionCache.objects.getOrPut(this) {
        getFields().firstOrNull {
            with(it.getType().kotlin as KClassImpl<*>) {
                descriptor.getKind() == ClassKind.OBJECT && !descriptor.isCompanionObject()
        }}?.get(null)?:NullMask
    }.unmask()
}

fun Class<*>.companionObjectInstance(): Any? {
    return ReflectionCache.companionObjects.getOrPut(this) {
        getFields().firstOrNull { (it.getType().kotlin as KClassImpl<*>).descriptor.isCompanionObject() }?.get(null) ?: NullMask
    }.unmask()
}

[suppress("UNCHECKED_CAST")]
fun <T> KClass<out T>.propertyGetter(property: String): KMemberProperty<Any, *>? {
    return ReflectionCache.propertyGetters.getOrPut(Pair(this, property)) {
        properties.singleOrNull {
            property == it.javaField?.getName() ?: it.javaGetter?.getName()?.removePrefix("get")
        } as KMemberProperty<Any, *>?
    }
}

fun Any.propertyValue(property: String): Any? {
    val getter = javaClass.kotlin.propertyGetter(property) ?: error("Invalid property ${property} on type ${javaClass.getName()}")
    return getter.get(this)
}

private fun Class<*>.consMetaData(): Triple<Constructor<*>, Array<Class<*>>, List<ValueParameterDescriptor>> {
    return ReflectionCache.consMetadata.getOrPut(this) {
        val cons = primaryConstructor() ?: error("Expecting single constructor for the bean")
        val consDesc = (this.kotlin as KClassImpl<*>).descriptor.getUnsubstitutedPrimaryConstructor()!!
        return Triple(cons, cons.getParameterTypes(), consDesc.getValueParameters())
    }
}

public class MissingArgumentException(val name: String) : RuntimeException("Required argument $name is missing")

[suppress("UNCHECKED_CAST")]
fun <T> Class<out T>.buildBeanInstance(params: (String) -> String?): T {
    objectInstance()?.let {
        return it as T
    }

    val (ktor, paramTypes, valueParams) = consMetaData()

    val args = valueParams.mapIndexed { i, param ->
        params(param.getName().asString())?.let {
            Serialization.deserialize(it, paramTypes[i] as Class<Any>)
        } ?: if (param.getType().isMarkedNullable()) {
            null
        } else {
            throw MissingArgumentException(param.getName().asString())

        }
    }.toTypedArray()

    return ktor.newInstance(*args) as T
}

fun Any.primaryProperties() : List<String> {
    return ReflectionCache.primaryProperites.getOrPut(javaClass) {
        (javaClass.kotlin as KClassImpl<*>).descriptor
                .getUnsubstitutedPrimaryConstructor()?.getValueParameters()
                ?.map { it.getName().asString() }
    }
}

[suppress("UNCHECKED_CAST")]
fun <T> Class<out T>.primaryConstructor() : Constructor<T>? {
    return getConstructors().singleOrNull() as? Constructor<T>
}

public fun Class<*>.isEnumClass(): Boolean = javaClass<Enum<*>>().isAssignableFrom(this)

fun ClassLoader?.findClasses(prefix: String, cache: MutableMap<Pair<Int, String>, List<Class<*>>>) : List<Class<*>> {
    synchronized(cache) {
        if (this == null) return emptyList()
        return cache.getOrPut(this.hashCode() to prefix) {
            getParent().findClasses(prefix, cache) + scanForClasses(prefix)
        }
    }
}

fun ClassLoader.scanForClasses(prefix: String) : List<Class<*>> {
    val urls = arrayListOf<URL>()
    val clazzz = arrayListOf<Class<*>>()

    (this as? URLClassLoader)?.getURLs()?.let {
        urls.addAll(it)
    } ?: run {
        val enumeration = this.getResources("")
        while(enumeration.hasMoreElements()) {
            urls.add(enumeration.nextElement())
        }
    }
    clazzz.addAll(urls.map {
            it.scanForClasses(prefix, this@scanForClasses)
        }.flatten())
    return clazzz
}

private fun URL.scanForClasses(prefix: String = "", classLoader: ClassLoader): List<Class<*>> {
    val path = urlDecode(getPath())
    return when {
        getFile().endsWith(".jar") -> JarFile(path).scanForClasses(prefix, classLoader)
        else -> File(path).scanForClasses(prefix, classLoader)
    }
}

private fun String.packageToPath() = replace(".", File.separator) + File.separator

private fun File.scanForClasses(prefix: String, classLoader: ClassLoader): List<Class<*>> {
    val path = prefix.packageToPath()
    return FileTreeWalk(this, filter = {
        it.isDirectory() || (it.isFile() && it.extension == "class")
    }).toList()
    .filter{
        it.isFile() && it.getAbsolutePath().contains(path)
    }.map {
        classLoader.tryLoadClass(prefix +"." + it.getAbsolutePath().substringAfterLast(path).removeSuffix(".class").replace(File.separator, "."))
    }.filterNotNull().toList()
}

private fun JarFile.scanForClasses(prefix: String, classLoader: ClassLoader): List<Class<*>> {
    val classes = arrayListOf<Class<*>>()
    val path = prefix.replace(".", "/") + "/"
    val entries = this.entries()
    while(entries.hasMoreElements()) {
        entries.nextElement().let {
            if (!it.isDirectory() && it.getName().endsWith(".class") && it.getName().contains(path)) {
                classes.addIfNotNull(classLoader.tryLoadClass(prefix + "." + it.getName().substringAfterLast(path).removeSuffix(".class").replace("/", ".")))
            }
        }
    }
    return classes
}

suppress("UNCHECKED_CAST")
fun <T> Iterable<Class<*>>.filterIsAssignable(clazz: Class<T>): List<Class<T>> = filter { clazz.isAssignableFrom(it) } as List<Class<T>>

suppress("UNCHECKED_CAST")
inline fun <reified T> Iterable<Class<*>>.filterIsAssignable(): List<Class<T>> = filterIsAssignable(javaClass<T>())
