package com.aol.cyclops.reactor.collections.extensions.persistent;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Random;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;
import org.pcollections.PVector;
import org.reactivestreams.Publisher;

import com.aol.cyclops.Monoid;
import com.aol.cyclops.Reducer;
import com.aol.cyclops.Reducers;
import com.aol.cyclops.control.Matchable.CheckValue1;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.control.Trampoline;
import com.aol.cyclops.data.collections.extensions.FluentCollectionX;
import com.aol.cyclops.data.collections.extensions.persistent.PVectorX;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.reactor.Fluxes;
import com.aol.cyclops.reactor.collections.extensions.base.AbstractFluentCollectionX;
import com.aol.cyclops.reactor.collections.extensions.base.LazyFluentCollection;
import com.aol.cyclops.reactor.collections.extensions.base.NativePlusLoop;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Wither;
import reactor.core.publisher.Flux;

/**
 * An extended List type {@see java.util.List}. 
 * This makes use of PVector (@see org.pcollections.PStack) from PCollectons. PVector is a persistent analogue of  the 
 * imperative ArrayList type.
 * Extended List operations execute lazily e.g.
 * <pre>
 * {@code 
 *    LazyPVectorX<Integer> q = LazyPVectorX.of(1,2,3)
 *                                          .map(i->i*2);
 * }
 * </pre>
 * The map operation above is not executed immediately. It will only be executed when (if) the data inside the
 * PVector is accessed. This allows lazy operations to be chained and executed more efficiently e.g.
 * 
 * <pre>
 * {@code 
 *    LazyPVectorX<Integer> q = LazyPVectorX.of(1,2,3)
 *                                          .map(i->i*2);
 *                                          .filter(i->i<5);
 * }
 * </pre>
 * 
 * The operation above is more efficient than the equivalent operation with a PVectorX.
 * 
 * @author johnmcclean
 *
 * @param <T> the type of elements held in this collection
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LazyPVectorX<T> extends AbstractFluentCollectionX<T>implements PVectorX<T>, NativePlusLoop<T> {
    private final LazyFluentCollection<T, PVector<T>> lazy;
    @Getter
    @Wither
    private final Reducer<PVector<T>> collector;

    @Override
    public LazyPVectorX<T> plusLoop(int max, IntFunction<T> value){
        PVector<T> vector = lazy.get();
        if(vector instanceof NativePlusLoop){
            return (LazyPVectorX<T>) ((NativePlusLoop)vector).plusLoop(max, value);
        }else{
            return (LazyPVectorX<T>) super.plusLoop(max, value);
        }
    }
    @Override
    public LazyPVectorX<T> plusLoop(Supplier<Optional<T>> supplier){
        PVector<T> vector = lazy.get();
        if(vector instanceof NativePlusLoop){
            return (LazyPVectorX<T>) ((NativePlusLoop)vector).plusLoop(supplier);
        }else{
            return (LazyPVectorX<T>) super.plusLoop(supplier);
        }
    }
    
    public static <T> LazyPVectorX<T> fromPVector(PVector<T> vec,Reducer<PVector<T>> collector){
        return new LazyPVectorX<T>(vec,collector);
    }
    
    public static <T> LazyPVectorX<T> fromFlux(Flux<T> vec,Reducer<PVector<T>> collector){
        return new LazyPVectorX<T>(vec,collector);
    }
    /**
     * Create a LazyPVectorX from a Stream
     * 
     * @param stream to construct a LazyQueueX from
     * @return LazyPVectorX
     */
    public static <T> LazyPVectorX<T> fromStreamS(Stream<T> stream) {
        return new LazyPVectorX<T>(
                                   Flux.from(ReactiveSeq.fromStream(stream)));
    }

    /**
     * Create a LazyPVectorX that contains the Integers between start and end
     * 
     * @param start
     *            Number of range to start from
     * @param end
     *            Number for range to end at
     * @return Range ListX
     */
    public static LazyPVectorX<Integer> range(int start, int end) {
        return fromStreamS(ReactiveSeq.range(start, end));
    }

    /**
     * Create a LazyPVectorX that contains the Longs between start and end
     * 
     * @param start
     *            Number of range to start from
     * @param end
     *            Number for range to end at
     * @return Range ListX
     */
    public static LazyPVectorX<Long> rangeLong(long start, long end) {
        return fromStreamS(ReactiveSeq.rangeLong(start, end));
    }

    /**
     * Unfold a function into a ListX
     * 
     * <pre>
     * {@code 
     *  LazyPVectorX.unfold(1,i->i<=6 ? Optional.of(Tuple.tuple(i,i+1)) : Optional.empty());
     * 
     * //(1,2,3,4,5)
     * 
     * }</pre>
     * 
     * @param seed Initial value 
     * @param unfolder Iteratively applied function, terminated by an empty Optional
     * @return ListX generated by unfolder function
     */
    public static <U, T> LazyPVectorX<T> unfold(U seed, Function<? super U, Optional<Tuple2<T, U>>> unfolder) {
        return fromStreamS(ReactiveSeq.unfold(seed, unfolder));
    }

    /**
     * Generate a LazyPVectorX from the provided Supplier up to the provided limit number of times
     * 
     * @param limit Max number of elements to generate
     * @param s Supplier to generate ListX elements
     * @return ListX generated from the provided Supplier
     */
    public static <T> LazyPVectorX<T> generate(long limit, Supplier<T> s) {

        return fromStreamS(ReactiveSeq.generate(s)
                                      .limit(limit));
    }

    /**
     * Create a LazyPVectorX by iterative application of a function to an initial element up to the supplied limit number of times
     * 
     * @param limit Max number of elements to generate
     * @param seed Initial element
     * @param f Iteratively applied to each element to generate the next element
     * @return ListX generated by iterative application
     */
    public static <T> LazyPVectorX<T> iterate(long limit, final T seed, final UnaryOperator<T> f) {
        return fromStreamS(ReactiveSeq.iterate(seed, f)
                                      .limit(limit));
    }

    /**
     * @return A collector that generates a LazyPVectorX
     */
    public static <T> Collector<T, ?, LazyPVectorX<T>> lazyListXCollector() {
        return Collectors.toCollection(() -> LazyPVectorX.of());
    }

    /**
     * @return An empty LazyPVectorX
     */
    public static <T> LazyPVectorX<T> empty() {
        return fromIterable((List<T>) ListX.<T> defaultCollector()
                                           .supplier()
                                           .get());
    }

    /**
     * Create a LazyPVectorX from the specified values
     * <pre>
     * {@code 
     *     ListX<Integer> lazy = LazyPVectorX.of(1,2,3,4,5);
     *     
     *     //lazily map List
     *     ListX<String> mapped = lazy.map(i->"mapped " +i); 
     *     
     *     String value = mapped.get(0); //transformation triggered now
     * }
     * </pre>
     * 
     * @param values To populate LazyPVectorX with
     * @return LazyPVectorX
     */
    @SafeVarargs
    public static <T> LazyPVectorX<T> of(T... values) {
        List<T> res = (List<T>) ListX.<T> defaultCollector()
                                     .supplier()
                                     .get();
        for (T v : values)
            res.add(v);
        return fromIterable(res);
    }

    /**
     * Construct a LazyPVectorX with a single value
     * <pre>
     * {@code 
     *    ListX<Integer> lazy = LazyPVectorX.singleton(5);
     *    
     * }
     * </pre>
     * 
     * 
     * @param value To populate LazyPVectorX with
     * @return LazyPVectorX with a single value
     */
    public static <T> LazyPVectorX<T> singleton(T value) {
        return LazyPVectorX.<T> of(value);
    }

    /**
     * Construct a LazyPVectorX from an Publisher
     * 
     * @param publisher
     *            to construct LazyPVectorX from
     * @return ListX
     */
    public static <T> LazyPVectorX<T> fromPublisher(Publisher<? extends T> publisher) {
        return fromStreamS(ReactiveSeq.fromPublisher((Publisher<T>) publisher));
    }

    /**
     * Construct LazyPVectorX from an Iterable
     * 
     * @param it to construct LazyPVectorX from
     * @return LazyPVectorX from Iterable
     */
    public static <T> LazyPVectorX<T> fromIterable(Iterable<T> it) {
        return fromIterable(Reducers.toPVector(), it);
    }

    /**
     * Construct a LazyPVectorX from an Iterable, using the specified Collector.
     * 
     * @param collector To generate Lists from, this can be used to create mutable vs immutable Lists (for example), or control List type (ArrayList, LinkedList)
     * @param it Iterable to construct LazyPVectorX from
     * @return Newly constructed LazyPVectorX
     */
    public static <T> LazyPVectorX<T> fromIterable(Reducer<PVector<T>> collector, Iterable<T> it) {
        if (it instanceof LazyPVectorX)
            return (LazyPVectorX<T>) it;

        if (it instanceof PVector)
            return new LazyPVectorX<T>(
                                       (PVector<T>) it, collector);

        return new LazyPVectorX<T>(
                                   Flux.fromIterable(it), collector);
    }

    private LazyPVectorX(PVector<T> list, Reducer<PVector<T>> collector) {
        this.lazy = new PersistentLazyCollection<T, PVector<T>>(
                                                                list, null, collector);
        this.collector = collector;
    }

    private LazyPVectorX(boolean efficientOps, PVector<T> list, Reducer<PVector<T>> collector) {
        this.lazy = new PersistentLazyCollection<T, PVector<T>>(
                                                                list, null, collector);
        this.collector = collector;
    }

    

    private LazyPVectorX(PVector<T> list) {
        this.collector = Reducers.toPVector();
        this.lazy = new PersistentLazyCollection<T, PVector<T>>(
                                                                list, null, Reducers.toPVector());
    }
    
    public LazyPVectorX(Flux<T> stream, Reducer<PVector<T>> collector) {
        this.collector = collector;
        this.lazy = new PersistentLazyCollection<>(
                                                   null, stream, Reducers.toPVector());
    }

    private LazyPVectorX(Flux<T> stream) {
        this.collector = Reducers.toPVector();
        this.lazy = new PersistentLazyCollection<>(
                                                   null, stream, collector);
    }

    private LazyPVectorX() {
        this.collector = Reducers.toPVector();
        this.lazy = new PersistentLazyCollection<>(
                                                   (PVector) this.collector.zero(), null, collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Iterable#forEach(java.util.function.Consumer)
     */
    @Override
    public void forEach(Consumer<? super T> action) {
        getVector().forEach(action);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Iterable#iterator()
     */
    @Override
    public Iterator<T> iterator() {
        return getVector().iterator();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#size()
     */
    @Override
    public int size() {
        return getVector().size();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#contains(java.lang.Object)
     */
    @Override
    public boolean contains(Object e) {
        return getVector().contains(e);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        return getVector().equals(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#isEmpty()
     */
    @Override
    public boolean isEmpty() {
        return getVector().isEmpty();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getVector().hashCode();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#toArray()
     */
    @Override
    public Object[] toArray() {
        return getVector().toArray();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#removeAll(java.util.Collection)
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        return getVector().removeAll(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#toArray(java.lang.Object[])
     */
    @Override
    public <T> T[] toArray(T[] a) {
        return getVector().toArray(a);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#add(java.lang.Object)
     */
    @Override
    public boolean add(T e) {
        return getVector().add(e);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#remove(java.lang.Object)
     */
    @Override
    public boolean remove(Object o) {
        return getVector().remove(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#containsAll(java.util.Collection)
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        return getVector().containsAll(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#addAll(java.util.Collection)
     */
    @Override
    public boolean addAll(Collection<? extends T> c) {
        return getVector().addAll(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#retainAll(java.util.Collection)
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        return getVector().retainAll(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#clear()
     */
    @Override
    public void clear() {
        getVector().clear();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getVector().toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jooq.lambda.Collectable#collect(java.util.stream.Collector)
     */
    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return stream().collect(collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jooq.lambda.Collectable#count()
     */
    @Override
    public long count() {
        return this.size();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#addAll(int, java.util.Collection)
     */
    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        return getVector().addAll(index, c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#replaceAll(java.util.function.UnaryOperator)
     */
    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        getVector().replaceAll(operator);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#removeIf(java.util.function.Predicate)
     */
    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        return getVector().removeIf(filter);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#sort(java.util.Comparator)
     */
    @Override
    public void sort(Comparator<? super T> c) {
        getVector().sort(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#get(int)
     */
    @Override
    public T get(int index) {
        return getVector().get(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#set(int, java.lang.Object)
     */
    @Override
    public T set(int index, T element) {
        return getVector().set(index, element);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#add(int, java.lang.Object)
     */
    @Override
    public void add(int index, T element) {
        getVector().add(index, element);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#remove(int)
     */
    @Override
    public T remove(int index) {
        return getVector().remove(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#parallelStream()
     */
    @Override
    public Stream<T> parallelStream() {
        return getVector().parallelStream();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#indexOf(java.lang.Object)
     */
    @Override
    public int indexOf(Object o) {
        // return
        // stream().zipWithIndex().filter(t->Objects.equals(t.v1,o)).findFirst().get().v2.intValue();
        return getVector().indexOf(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#lastIndexOf(java.lang.Object)
     */
    @Override
    public int lastIndexOf(Object o) {
        return getVector().lastIndexOf(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#listIterator()
     */
    @Override
    public ListIterator<T> listIterator() {
        return getVector().listIterator();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#listIterator(int)
     */
    @Override
    public ListIterator<T> listIterator(int index) {
        return getVector().listIterator(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.data.collections.extensions.standard.ListX#subList(int,
     * int)
     */
    @Override
    public LazyPVectorX<T> subList(int fromIndex, int toIndex) {
        return new LazyPVectorX<T>(
                                   getVector().subList(fromIndex, toIndex), getCollector());
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Iterable#spliterator()
     */
    @Override
    public Spliterator<T> spliterator() {
        return getVector().spliterator();
    }

    /**
     * @return PStack
     */
    private PVector<T> getVector() {
        return lazy.get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#stream(reactor.core.publisher.Flux)
     */
    @Override
    public <X> LazyPVectorX<X> stream(Flux<X> stream) {
        return new LazyPVectorX<X>(
                                   stream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#flux()
     */
    @Override
    public Flux<T> flux() {
        return lazy.flux();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#combine(java.util.function.BiPredicate,
     * java.util.function.BinaryOperator)
     */
    @Override
    public LazyPVectorX<T> combine(BiPredicate<? super T, ? super T> predicate, BinaryOperator<T> op) {

        return (LazyPVectorX<T>) super.combine(predicate, op);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#reverse()
     */
    @Override
    public LazyPVectorX<T> reverse() {

        return (LazyPVectorX<T>) super.reverse();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#filter(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> filter(Predicate<? super T> pred) {

        return (LazyPVectorX<T>) super.filter(pred);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#map(java.util.function.Function)
     */
    @Override
    public <R> LazyPVectorX<R> map(Function<? super T, ? extends R> mapper) {

        return (LazyPVectorX<R>) super.map(mapper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#flatMap(java.util.function.Function)
     */
    @Override
    public <R> LazyPVectorX<R> flatMap(Function<? super T, ? extends Iterable<? extends R>> mapper) {
        return (LazyPVectorX<R>) super.flatMap(mapper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#limit(long)
     */
    @Override
    public LazyPVectorX<T> limit(long num) {
        return (LazyPVectorX<T>) super.limit(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#skip(long)
     */
    @Override
    public LazyPVectorX<T> skip(long num) {
        return (LazyPVectorX<T>) super.skip(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#takeRight(int)
     */
    @Override
    public LazyPVectorX<T> takeRight(int num) {
        return (LazyPVectorX<T>) super.takeRight(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#dropRight(int)
     */
    @Override
    public LazyPVectorX<T> dropRight(int num) {
        return (LazyPVectorX<T>) super.dropRight(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#takeWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> takeWhile(Predicate<? super T> p) {
        return (LazyPVectorX<T>) super.takeWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#dropWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> dropWhile(Predicate<? super T> p) {
        return (LazyPVectorX<T>) super.dropWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#takeUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> takeUntil(Predicate<? super T> p) {
        return (LazyPVectorX<T>) super.takeUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#dropUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> dropUntil(Predicate<? super T> p) {
        return (LazyPVectorX<T>) super.dropUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#trampoline(java.util.function.Function)
     */
    @Override
    public <R> LazyPVectorX<R> trampoline(Function<? super T, ? extends Trampoline<? extends R>> mapper) {
        return (LazyPVectorX<R>) super.trampoline(mapper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#slice(long, long)
     */
    @Override
    public LazyPVectorX<T> slice(long from, long to) {
        return (LazyPVectorX<T>) super.slice(from, to);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#grouped(int)
     */
    @Override
    public LazyPVectorX<ListX<T>> grouped(int groupSize) {

        return (LazyPVectorX<ListX<T>>) super.grouped(groupSize);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#grouped(java.util.function.Function,
     * java.util.stream.Collector)
     */
    @Override
    public <K, A, D> LazyPVectorX<Tuple2<K, D>> grouped(Function<? super T, ? extends K> classifier,
            Collector<? super T, A, D> downstream) {

        return (LazyPVectorX) super.grouped(classifier, downstream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#grouped(java.util.function.Function)
     */
    @Override
    public <K> LazyPVectorX<Tuple2<K, Seq<T>>> grouped(Function<? super T, ? extends K> classifier) {

        return (LazyPVectorX) super.grouped(classifier);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(java.lang.Iterable)
     */
    @Override
    public <U> LazyPVectorX<Tuple2<T, U>> zip(Iterable<? extends U> other) {

        return (LazyPVectorX) super.zip(other);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(java.lang.Iterable,
     * java.util.function.BiFunction)
     */
    @Override
    public <U, R> LazyPVectorX<R> zip(Iterable<? extends U> other,
            BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (LazyPVectorX<R>) super.zip(other, zipper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#sliding(int)
     */
    @Override
    public LazyPVectorX<ListX<T>> sliding(int windowSize) {

        return (LazyPVectorX<ListX<T>>) super.sliding(windowSize);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#sliding(int, int)
     */
    @Override
    public LazyPVectorX<ListX<T>> sliding(int windowSize, int increment) {

        return (LazyPVectorX<ListX<T>>) super.sliding(windowSize, increment);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#scanLeft(com.aol.cyclops.Monoid)
     */
    @Override
    public LazyPVectorX<T> scanLeft(Monoid<T> monoid) {

        return (LazyPVectorX<T>) super.scanLeft(monoid);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#scanLeft(java.lang.Object,
     * java.util.function.BiFunction)
     */
    @Override
    public <U> LazyPVectorX<U> scanLeft(U seed, BiFunction<? super U, ? super T, ? extends U> function) {

        return (LazyPVectorX<U>) super.scanLeft(seed, function);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#scanRight(com.aol.cyclops.Monoid)
     */
    @Override
    public LazyPVectorX<T> scanRight(Monoid<T> monoid) {

        return (LazyPVectorX<T>) super.scanRight(monoid);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#scanRight(java.lang.Object,
     * java.util.function.BiFunction)
     */
    @Override
    public <U> LazyPVectorX<U> scanRight(U identity, BiFunction<? super T, ? super U, ? extends U> combiner) {

        return (LazyPVectorX<U>) super.scanRight(identity, combiner);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#sorted(java.util.function.Function)
     */
    @Override
    public <U extends Comparable<? super U>> LazyPVectorX<T> sorted(Function<? super T, ? extends U> function) {

        return (LazyPVectorX<T>) super.sorted(function);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#plusLazy(java.lang.Object)
     */
    @Override
    public LazyPVectorX<T> plusLazy(T e) {

        return (LazyPVectorX<T>) super.plusLazy(e);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#plusAllLazy(java.util.Collection)
     */
    @Override
    public LazyPVectorX<T> plusAllLazy(Collection<? extends T> list) {

        return (LazyPVectorX<T>) super.plusAllLazy(list);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#minusLazy(java.lang.Object)
     */
    @Override
    public LazyPVectorX<T> minusLazy(Object e) {

        return (LazyPVectorX<T>) super.minusLazy(e);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#minusAllLazy(java.util.Collection)
     */
    @Override
    public LazyPVectorX<T> minusAllLazy(Collection<?> list) {

        return (LazyPVectorX<T>) super.minusAllLazy(list);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#cycle(int)
     */
    @Override
    public LazyPVectorX<T> cycle(int times) {

        return (LazyPVectorX<T>) super.cycle(times);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#cycle(com.aol.cyclops.Monoid, int)
     */
    @Override
    public LazyPVectorX<T> cycle(Monoid<T> m, int times) {

        return (LazyPVectorX<T>) super.cycle(m, times);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#cycleWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> cycleWhile(Predicate<? super T> predicate) {

        return (LazyPVectorX<T>) super.cycleWhile(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#cycleUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> cycleUntil(Predicate<? super T> predicate) {

        return (LazyPVectorX<T>) super.cycleUntil(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(org.jooq.lambda.Seq)
     */
    @Override
    public <U> LazyPVectorX<Tuple2<T, U>> zip(Seq<? extends U> other) {

        return (LazyPVectorX) super.zip(other);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip3(java.util.stream.Stream,
     * java.util.stream.Stream)
     */
    @Override
    public <S, U> LazyPVectorX<Tuple3<T, S, U>> zip3(Stream<? extends S> second, Stream<? extends U> third) {

        return (LazyPVectorX) super.zip3(second, third);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip4(java.util.stream.Stream,
     * java.util.stream.Stream, java.util.stream.Stream)
     */
    @Override
    public <T2, T3, T4> LazyPVectorX<Tuple4<T, T2, T3, T4>> zip4(Stream<? extends T2> second,
            Stream<? extends T3> third, Stream<? extends T4> fourth) {

        return (LazyPVectorX) super.zip4(second, third, fourth);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zipWithIndex()
     */
    @Override
    public LazyPVectorX<Tuple2<T, Long>> zipWithIndex() {

        return (LazyPVectorX<Tuple2<T, Long>>) super.zipWithIndex();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#distinct()
     */
    @Override
    public LazyPVectorX<T> distinct() {

        return (LazyPVectorX<T>) super.distinct();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#sorted()
     */
    @Override
    public LazyPVectorX<T> sorted() {

        return (LazyPVectorX<T>) super.sorted();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#sorted(java.util.Comparator)
     */
    @Override
    public LazyPVectorX<T> sorted(Comparator<? super T> c) {

        return (LazyPVectorX<T>) super.sorted(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#skipWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> skipWhile(Predicate<? super T> p) {

        return (LazyPVectorX<T>) super.skipWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#skipUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> skipUntil(Predicate<? super T> p) {

        return (LazyPVectorX<T>) super.skipUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#limitWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> limitWhile(Predicate<? super T> p) {

        return (LazyPVectorX<T>) super.limitWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#limitUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> limitUntil(Predicate<? super T> p) {

        return (LazyPVectorX<T>) super.limitUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#intersperse(java.lang.Object)
     */
    @Override
    public LazyPVectorX<T> intersperse(T value) {

        return (LazyPVectorX<T>) super.intersperse(value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#shuffle()
     */
    @Override
    public LazyPVectorX<T> shuffle() {

        return (LazyPVectorX<T>) super.shuffle();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#skipLast(int)
     */
    @Override
    public LazyPVectorX<T> skipLast(int num) {

        return (LazyPVectorX<T>) super.skipLast(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#limitLast(int)
     */
    @Override
    public LazyPVectorX<T> limitLast(int num) {

        return (LazyPVectorX<T>) super.limitLast(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#onEmpty(java.lang.Object)
     */
    @Override
    public LazyPVectorX<T> onEmpty(T value) {

        return (LazyPVectorX<T>) super.onEmpty(value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#onEmptyGet(java.util.function.Supplier)
     */
    @Override
    public LazyPVectorX<T> onEmptyGet(Supplier<? extends T> supplier) {

        return (LazyPVectorX<T>) super.onEmptyGet(supplier);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#onEmptyThrow(java.util.function.Supplier)
     */
    @Override
    public <X extends Throwable> LazyPVectorX<T> onEmptyThrow(Supplier<? extends X> supplier) {

        return (LazyPVectorX<T>) super.onEmptyThrow(supplier);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#shuffle(java.util.Random)
     */
    @Override
    public LazyPVectorX<T> shuffle(Random random) {

        return (LazyPVectorX<T>) super.shuffle(random);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#ofType(java.lang.Class)
     */
    @Override
    public <U> LazyPVectorX<U> ofType(Class<? extends U> type) {

        return (LazyPVectorX<U>) super.ofType(type);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#filterNot(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<T> filterNot(Predicate<? super T> fn) {

        return (LazyPVectorX<T>) super.filterNot(fn);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#notNull()
     */
    @Override
    public LazyPVectorX<T> notNull() {

        return (LazyPVectorX<T>) super.notNull();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#removeAll(java.util.stream.Stream)
     */
    @Override
    public LazyPVectorX<T> removeAll(Stream<? extends T> stream) {

        return (LazyPVectorX<T>) (super.removeAll(stream));
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#removeAll(org.jooq.lambda.Seq)
     */
    @Override
    public LazyPVectorX<T> removeAll(Seq<? extends T> stream) {

        return (LazyPVectorX<T>) super.removeAll(stream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#removeAll(java.lang.Iterable)
     */
    @Override
    public LazyPVectorX<T> removeAll(Iterable<? extends T> it) {

        return (LazyPVectorX<T>) super.removeAll(it);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#removeAll(java.lang.Object[])
     */
    @Override
    public LazyPVectorX<T> removeAll(T... values) {

        return (LazyPVectorX<T>) super.removeAll(values);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#retainAll(java.lang.Iterable)
     */
    @Override
    public LazyPVectorX<T> retainAll(Iterable<? extends T> it) {

        return (LazyPVectorX<T>) super.retainAll(it);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#retainAll(java.util.stream.Stream)
     */
    @Override
    public LazyPVectorX<T> retainAll(Stream<? extends T> stream) {

        return (LazyPVectorX<T>) super.retainAll(stream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#retainAll(org.jooq.lambda.Seq)
     */
    @Override
    public LazyPVectorX<T> retainAll(Seq<? extends T> stream) {

        return (LazyPVectorX<T>) super.retainAll(stream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#retainAll(java.lang.Object[])
     */
    @Override
    public LazyPVectorX<T> retainAll(T... values) {

        return (LazyPVectorX<T>) super.retainAll(values);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#cast(java.lang.Class)
     */
    @Override
    public <U> LazyPVectorX<U> cast(Class<? extends U> type) {

        return (LazyPVectorX<U>) super.cast(type);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#patternMatch(java.util.function.Function,
     * java.util.function.Supplier)
     */
    @Override
    public <R> LazyPVectorX<R> patternMatch(Function<CheckValue1<T, R>, CheckValue1<T, R>> case1,
            Supplier<? extends R> otherwise) {

        return (LazyPVectorX<R>) super.patternMatch(case1, otherwise);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#permutations()
     */
    @Override
    public LazyPVectorX<ReactiveSeq<T>> permutations() {

        return (LazyPVectorX<ReactiveSeq<T>>) super.permutations();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#combinations(int)
     */
    @Override
    public LazyPVectorX<ReactiveSeq<T>> combinations(int size) {

        return (LazyPVectorX<ReactiveSeq<T>>) super.combinations(size);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#combinations()
     */
    @Override
    public LazyPVectorX<ReactiveSeq<T>> combinations() {

        return (LazyPVectorX<ReactiveSeq<T>>) super.combinations();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#grouped(int, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> LazyPVectorX<C> grouped(int size, Supplier<C> supplier) {

        return (LazyPVectorX<C>) super.grouped(size, supplier);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#groupedUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<ListX<T>> groupedUntil(Predicate<? super T> predicate) {

        return (LazyPVectorX<ListX<T>>) super.groupedUntil(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#groupedWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPVectorX<ListX<T>> groupedWhile(Predicate<? super T> predicate) {

        return (LazyPVectorX<ListX<T>>) super.groupedWhile(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#groupedWhile(java.util.function.Predicate,
     * java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> LazyPVectorX<C> groupedWhile(Predicate<? super T> predicate,
            Supplier<C> factory) {

        return (LazyPVectorX<C>) super.groupedWhile(predicate, factory);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#groupedUntil(java.util.function.Predicate,
     * java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> LazyPVectorX<C> groupedUntil(Predicate<? super T> predicate,
            Supplier<C> factory) {

        return (LazyPVectorX<C>) super.groupedUntil(predicate, factory);
    }

    /** PStackX methods **/

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.data.collections.extensions.standard.ListX#with(int,
     * java.lang.Object)
     */
    public LazyPVectorX<T> with(int i, T element) {
        return stream(Fluxes.insertAt(Fluxes.deleteBetween(flux(), i, i + 1), i, element));
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#groupedStatefullyUntil(java.util.function.
     * BiPredicate)
     */
    @Override
    public LazyPVectorX<ListX<T>> groupedStatefullyUntil(BiPredicate<ListX<? super T>, ? super T> predicate) {

        return (LazyPVectorX<ListX<T>>) super.groupedStatefullyUntil(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#peek(java.util.function.Consumer)
     */
    @Override
    public LazyPVectorX<T> peek(Consumer<? super T> c) {

        return (LazyPVectorX) super.peek(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(org.jooq.lambda.Seq,
     * java.util.function.BiFunction)
     */
    @Override
    public <U, R> LazyPVectorX<R> zip(Seq<? extends U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (LazyPVectorX<R>) super.zip(other, zipper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(java.util.stream.Stream,
     * java.util.function.BiFunction)
     */
    @Override
    public <U, R> LazyPVectorX<R> zip(Stream<? extends U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (LazyPVectorX<R>) super.zip(other, zipper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(java.util.stream.Stream)
     */
    @Override
    public <U> LazyPVectorX<Tuple2<T, U>> zip(Stream<? extends U> other) {

        return (LazyPVectorX) super.zip(other);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(java.util.function.BiFunction,
     * org.reactivestreams.Publisher)
     */
    @Override
    public <T2, R> LazyPVectorX<R> zip(BiFunction<? super T, ? super T2, ? extends R> fn,
            Publisher<? extends T2> publisher) {

        return (LazyPVectorX<R>) super.zip(fn, publisher);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.data.collections.extensions.standard.ListX#onEmptySwitch(
     * java.util.function.Supplier)
     */
    @Override
    public LazyPVectorX<T> onEmptySwitch(Supplier<? extends PVector<T>> supplier) {
        return stream(Fluxes.onEmptySwitch(flux(), () -> Flux.fromIterable(supplier.get())));

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.data.collections.extensions.standard.ListX#unit(
     * Collection)
     */
    @Override
    public <R> LazyPVectorX<R> unit(Collection<R> col) {

        return fromIterable(col);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.types.Unit#unit(java.lang.Object)
     */
    @Override
    public <R> LazyPVectorX<R> unit(R value) {
        return singleton(value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#unitIterator(java.util.Iterator)
     */
    @Override
    public <R> LazyPVectorX<R> unitIterator(Iterator<R> it) {
        return fromIterable(() -> it);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.data.collections.extensions.persistent.PVectorX#emptyUnit
     * ()
     */
    @Override
    public <R> LazyPVectorX<R> emptyUnit() {

        return LazyPVectorX.<R> empty();
    }

    /*
     * This converted to PVector
     * 
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.data.collections.extensions.persistent.PVectorX#toPVector
     * ()
     */
    public LazyPVectorX<T> toPVector() {
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.reactor.collections.extensions.base.LazyFluentCollectionX
     * #plusInOrder(java.lang.Object)
     */
    @Override
    public LazyPVectorX<T> plusInOrder(T e) {
        return plus(size(), e);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.data.collections.extensions.CollectionX#stream()
     */
    @Override
    public ReactiveSeq<T> stream() {

        return ReactiveSeq.fromIterable(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#from(java.util.Collection)
     */
    @Override
    public <X> LazyPVectorX<X> from(Collection<X> col) {
        return fromIterable(col);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.data.collections.extensions.persistent.PVectorX#monoid()
     */
    @Override
    public <T> Reducer<PVector<T>> monoid() {

        return Reducers.toPVector();

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.MapPSet#plus(java.lang.Object)
     */
    public LazyPVectorX<T> plus(T e) {
        return new LazyPVectorX<T>(
                                   getVector().plus(e), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PStack#plus(int, java.lang.Object)
     */
    public LazyPVectorX<T> plus(int i, T e) {
        return new LazyPVectorX<T>(
                                   getVector().plus(i, e), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.MapPSet#minus(java.lang.Object)
     */
    public LazyPVectorX<T> minus(Object e) {
        return new LazyPVectorX<T>(
                                   getVector().minus(e), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.MapPSet#plusAll(java.util.Collection)
     */
    public LazyPVectorX<T> plusAll(Collection<? extends T> list) {
        return new LazyPVectorX<T>(
                                   getVector().plusAll(list), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PStack#plusAll(int, java.util.Collection)
     */
    public LazyPVectorX<T> plusAll(int i, Collection<? extends T> list) {
        return new LazyPVectorX<T>(
                                   getVector().plusAll(i, list), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.MapPSet#minusAll(java.util.Collection)
     */
    public LazyPVectorX<T> minusAll(Collection<?> list) {
        return new LazyPVectorX<T>(
                                   getVector().minusAll(list), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PStack#minus(int)
     */
    public LazyPVectorX<T> minus(int i) {
        return new LazyPVectorX<T>(
                                   getVector().minus(i), this.collector);
    }
    /* (non-Javadoc)
     * @see com.aol.cyclops.reactor.collections.extensions.base.LazyFluentCollectionX#materialize()
     */
    @Override
    public LazyPVectorX<T> materialize() {
       this.lazy.get();
       return this;
    }
}
