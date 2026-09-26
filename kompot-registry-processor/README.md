# kompot-registry-processor

KSP-процессор: по `@KompotComponentMarker` пишет полиморфную регистрацию компонента и запись
рендерера, чтобы ни один список не приходилось вести руками.

## Подключение

KSP ставится в модуль, который **объявляет** компоненты, с тегом, уникальным для этого модуля:

```kotlin
plugins { id("com.google.devtools.ksp") }

dependencies {
    implementation("io.github.youndie.kompot:kompot-registry-annotations")
    ksp("io.github.youndie.kompot:kompot-registry-processor")
}

ksp { arg("kompotModuleTag", "Catalogue") }
```

Тег обязан быть уникальным, потому что сгенерированные файлы ложатся в один пакет: два модуля с
одним тегом сгенерируют объекты с одинаковыми именами, и столкнутся они уже в сборке потребителя.
Сгенерированный код компилируется в артефакт объявляющего модуля, поэтому **потребитель KSP не
ставит** — он просто импортирует `generatedCatalogueSerializersModule`.

## Компонент и рендерер — в разных модулях

Как только есть сервер, компонент и его рендерер живут в разных модулях. Рендереру нужен Compose, а
у сервера его нет, так что компонент, объявленный рядом с рендерером, — компонент, который сервер не
может построить, а ровно для этого server-driven компонент и существует. Компонент объявляется в
модуле, от которого зависят обе стороны, рендерер — в Compose-модуле, у каждого свой тег; рендерер
несёт свой компонент в аргументе типа, поэтому сгенерированный реестр сводит их через границу
модулей:

```kotlin
// :catalogue-wire — без Compose, от него зависит сервер
@Serializable @SerialName("product_card") @KompotComponentMarker
data class ProductCardComponent(override val id: String, val title: String) : KompotComponent

// :catalogue-ui — ksp { arg("kompotModuleTag", "CatalogueUi") }
@KompotComponentMarker
class ProductCardRenderer : KompotComponentRenderer<ProductCardComponent> { … }
```

`kompot-forms` и `kompot-forms-client` — именно такая пара, поэтому разделение проверяется каждой
сборкой этого репозитория, а не только описано здесь.
