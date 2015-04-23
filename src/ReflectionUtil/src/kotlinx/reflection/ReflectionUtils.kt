package kotlinx.reflection

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.load.java.reflect.tryLoadClass
import java.util.*
import java.lang.reflect.*
import kotlin.Array
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass
import kotlin.reflect.KMemberProperty
import kotlin.reflect.jvm.*
import kotlin.reflect.jvm.internal.DescriptorBasedProperty
import kotlin.reflect.jvm.internal.KClassImpl
import java.io.File
import java.net.URL

object ReflectionCache {
    val objects = ConcurrentHashMap<Class<*>, Any>()
    val classObjects = ConcurrentHashMap<Class<*>, Any>()
    val consMetadata = ConcurrentHashMap<Class<*>, Pair<Constructor<*>?, Array<Class<*>>>>()
    val primaryProperites = ConcurrentHashMap<Class<*>, List<String>>()
    val properites = ConcurrentHashMap<Class<*>, List<String>>()
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
    return ReflectionCache.classObjects.getOrPut(this) {
        getFields().firstOrNull { (it.getType().kotlin as KClassImpl<*>).descriptor.getKind() == ClassKind.OBJECT }?.get(null)
    }
}


deprecated("use #companionObjectInstance() method instead")
fun Class<*>.classObjectInstance(): Any? {
    return ReflectionCache.classObjects.getOrPut(this) {
        try {
            val field = getDeclaredField("OBJECT\$")
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
                field[null]!!
            }
            else NullMask
        }
        catch (e: NoSuchFieldException) {
            NullMask
        }
    }.unmask()
}

fun Class<*>.companionObjectInstance(): Any? {
    return ReflectionCache.classObjects.getOrPut(this) {
        getFields().firstOrNull { (it.getType().kotlin as KClassImpl<*>).descriptor.isCompanionObject() }?.get(null)
    }
}

[suppress("UNCHECKED_CAST")]
fun <T> KClass<out T>.propertyGetter(property: String): KMemberProperty<Any, *>? {
    return ReflectionCache.propertyGetters.getOrPut(Pair(this, property)) {
        return properties.singleOrNull {
            property == it.javaField?.getName() ?: it.javaGetter?.getName()?.removePrefix("get")
        } as KMemberProperty<Any, *>?
    }
}

fun Any.propertyValue(property: String): Any? {
    with(javaClass.kotlin) {
        return propertyGetter(property)!!.get(this@propertyValue)
            ?: if ((this as KClassImpl).scope.getProperties(Name.identifier(property)).first().getType().isMarkedNullable()) {
                null
            } else {
            error("Invalid property ${property} on type ${javaClass.getName()}")
        }
    }
}

val emptyClasses : Array<Class<*>> = array()

private fun Class<*>.consMetaData(): Pair<Constructor<*>?, Array<Class<*>>> {
    return ReflectionCache.consMetadata.getOrPut(this) {
        with(primaryConstructor()) {
            return this to (this?.getParameterTypes() ?: emptyClasses)
        }
    }
}

public class MissingArgumentException(val name: String) : RuntimeException("Required argument $name is missing")

[suppress("UNCHECKED_CAST")]
fun <T> Class<out T>.buildBeanInstance(params: (String) -> String?): T {
    objectInstance()?.let {
        return@let it as T
    }

    val (ktor, paramTypes) = consMetaData()

    val args = (this.kotlin as KClassImpl<T>).descriptor.getUnsubstitutedPrimaryConstructor()?.getValueParameters()?.mapIndexed { i, param ->
        params(param.getName().asString())?.let {
            Serialization.deserialize(it, paramTypes[i] as Class<Any>)
        } ?: if (param.getType().isMarkedNullable()) {
            null
        } else {
            throw MissingArgumentException(param.getName().asString())

        }
    }?.toTypedArray() ?: arrayOf()


    return ktor!!.newInstance(*args) as T
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
    return getConstructors().firstOrNull() as? Constructor<T>
}

public fun Class<*>.isEnumClass(): Boolean = javaClass<Enum<*>>().isAssignableFrom(this)

fun ClassLoader.loadedClasses(prefix: String ="") : List<Class<*>> {
    return  ClassLoader::class.java.getDeclaredField("classes").let {
        it.setAccessible(true);
        val result = it.get(this)
        it.setAccessible(false)
        (result as Vector<Class<*>>).toArrayList()
    }.filter{
        !prefix.isNullOrBlank() && it.getPackage()?.getName().orEmpty().startsWith(prefix)
    }
}

fun ClassLoader.findClasses(prefix: String = "") : List<Class<*>> {
    val urls = arrayListOf<URL>()
    val enumeration = this.getResources("")
    while(enumeration.hasMoreElements()) {
        urls.add(enumeration.nextElement())
    }

    val prefixPath = prefix.replace(".", File.separator) + File.separator

    return urls.map {
        val root = File(it.getPath())
        FileTreeWalk(root, filter = {
            it.isDirectory() || (it.isFile() && it.extension == "class")
        }).toList()
        .filter{
              it.isFile() && it.getAbsolutePath().contains(prefixPath)
        }.map {
            Class.forName(prefix +"." + it.getAbsolutePath().substringAfterLast(prefixPath).removeSuffix(".class").replace(File.separator, "."))
        }.filterNotNull().toList()
    }.flatten()
}

suppress("UNCHECKED_CAST")
fun <T> Iterable<Class<*>>.filterIsAssignable(clazz: Class<T>): List<Class<T>> = filter { clazz.isAssignableFrom(it) } as List<Class<T>>

suppress("UNCHECKED_CAST")
inline fun <reified T> Iterable<Class<*>>.filterIsAssignable(): List<Class<T>> = filterIsAssignable(javaClass<T>())