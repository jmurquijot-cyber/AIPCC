# AIPCC

**Administración Integral para la Congregación Cristiana**

Aplicación Android basada en el código de AAPRC 1.8.4, con la corrección inicial del importador de Vida y Ministerio y una primera función de exportación CSV.

## Identidad inicial
- Nombre visible: ADMINISTRACION INTEGRAL PARA LA CONGREGACION CRISTIANA
- `applicationId`: `org.aipcc.congregacion`
- Versión inicial: `1.0.0` (`versionCode` 1)
- Lenguaje principal: Java
- Android Gradle Plugin / Gradle: según la configuración del proyecto.

## Compilar
El flujo de GitHub Actions compila una APK de depuración en cada push a `main`, pull request o ejecución manual. Descarga la APK desde la sección **Actions → ejecución del flujo → Artifacts**.

## Importación y exportación
La exportación añadida para Vida y Ministerio genera un CSV compatible con Excel, no un archivo XLSX nativo. Debe probarse en dispositivo antes de considerarse verificada. El importador se ha ajustado para reconocer nombres de etiquetas XML con prefijo, pero se requiere probarlo con las plantillas reales.

## Próximos pasos
1. Subir el contenido de este proyecto al repositorio GitHub `AIPCC`.
2. Ejecutar GitHub Actions y revisar el resultado de compilación.
3. Probar importación y exportación en un dispositivo de pruebas.
4. Revisar y definir el modelo de datos, permisos, sincronización y arquitectura de módulos independientes antes de implementar intercambio de datos entre equipos.

## Seguridad
No incluir contraseñas, tokens, claves de firma ni archivos de datos personales en el repositorio.
