package gt.umg.svbgua;

/** Elemento de un combo de filtro: la etiqueta que se ve y el valor real (null = "Todos"). */
record OpcionFiltro<T>(String etiqueta, T valor) {

    @Override
    public String toString() {
        return etiqueta;
    }
}
