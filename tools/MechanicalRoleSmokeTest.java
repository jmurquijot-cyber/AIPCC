package mx.escuelaministerio.app;

/** Prueba sin dependencias Android: ejecutar junto con Models.java. */
public final class MechanicalRoleSmokeTest {
    public static void main(String[] args) {
        Models.Student man=new Models.Student();man.sex="Hombre";man.microfono1=true;
        Models.Student woman=new Models.Student();woman.sex="Mujer";woman.microfono1=true;
        Models.Student importedWoman=new Models.Student();importedWoman.sex="M";
        if(!man.isMale() || woman.isMale() || importedWoman.isMale())throw new AssertionError("Filtro de varones incorrecto");
        if(!Models.MechanicalRole.MICROFONO_1.authorized(man))throw new AssertionError("Micrófono 1 debería estar habilitado");
        if(Models.MechanicalRole.MICROFONO_2.authorized(man))throw new AssertionError("Micrófono 2 requiere autorización propia");
        Models.MechanicalProgram program=new Models.MechanicalProgram();
        if(program.studentId(Models.MechanicalRole.ENTRADA)!=0)throw new AssertionError("Un puesto vacío debe quedar pendiente");
        program.assigned.put(Models.MechanicalRole.MICROFONO_1,42L);
        if(program.studentId(Models.MechanicalRole.MICROFONO_1)!=42L)throw new AssertionError("El puesto asignado no se conservó");
        System.out.println("Pruebas de funciones mecánicas correctas.");
    }
}
