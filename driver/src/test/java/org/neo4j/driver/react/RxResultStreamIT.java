/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.driver.react;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.summary.ResultSummary;
import org.neo4j.driver.v1.util.DatabaseExtension;
import org.neo4j.driver.v1.util.ParallelizableIT;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.driver.v1.Values.parameters;

@ParallelizableIT
class RxResultStream IT
{
    @RegisterExtension
    static final DatabaseExtension neo4j = new DatabaseExtension();

    @Test
    void shouldAllowIteratingOverResultStream()
    {
        // When
        RxSession session = neo4j.driver().rxSession();
        RxResult res = session.run( "UNWIND [1,2,3,4] AS a RETURN a" );

        // Then I should be able to iterate over the result
        AtomicInteger idx = new AtomicInteger( 1 );
        Flux.from( res.records() ).doOnNext( r -> assertEquals( idx.getAndIncrement(), r.get( "a" ).asLong() ) ).blockLast();
    }

    @Test
    void shouldHaveFieldNamesInResult()
    {
        // When
        RxSession session = neo4j.driver().rxSession();
        RxResult res = session.run( "CREATE (n:TestNode {name:'test'}) RETURN n" );

        // Then

        String keys = Mono.from( res.keys() ).block();
        String recordKeys = Mono.from( res.records() ).map( record -> record.keys().toString() ).block();

        assertEquals( "n", keys );
        assertEquals( "[n]", recordKeys );
    }

    @Test
    void shouldGiveHelpfulFailureMessageWhenAccessNonExistingField()
    {
        // Given
        RxSession session = neo4j.driver().rxSession();
        RxResult rs =
                session.run( "CREATE (n:Person {name:{name}}) RETURN n", parameters( "name", "Tom Hanks" ) );

        // When
        Record single = Mono.from( rs.records() ).block();

        // Then
        assertTrue( single.get( "m" ).isNull() );
    }

    @Test
    void shouldGiveHelpfulFailureMessageWhenAccessNonExistingPropertyOnNode()
    {
        // Given
        RxSession session = neo4j.driver().rxSession();
        RxResult rs =
                session.run( "CREATE (n:Person {name:{name}}) RETURN n", parameters( "name", "Tom Hanks" ) );

        // When
        Record single = Mono.from( rs.records() ).block();

        // Then
        assertTrue( single.get( "n" ).get( "age" ).isNull() );
    }

    @Test
    void shouldNotReturnNullKeysOnEmptyResult()
    {
        // Given
        RxSession session = neo4j.driver().rxSession();
        RxResult rs = session.run( "CREATE (n:Person {name:{name}})", parameters( "name", "Tom Hanks" ) );

        // THEN
        assertNotNull( Mono.from( rs.keys() ).block() );
        Mono.from( rs.records() ).block();
    }

    @Test
    void shouldBeAbleToReuseSessionAfterFailure()
    {
        // Given
        RxSession session = neo4j.driver().rxSession();
        RxResult res1 = session.run( "INVALID" );

        assertThrows( Exception.class, () -> Mono.from( res1.records() ).block() );

        // When
        RxResult res2 = session.run( "RETURN 1" );

        // Then
        Record record = Mono.from( res2.records() ).block();
        assertEquals( record.get("1").asLong(), 1L );
    }

    @Test
    void shouldBeAbleToAccessSummaryAfterFailure()
    {
        // Given
        RxSession session = neo4j.driver().rxSession();
        RxResult res1 = session.run( "INVALID" );
        AtomicReference<ResultSummary> summaryRef = new AtomicReference<>();

        // When
        Mono.from( res1.records() ).doFinally( signalType -> {
           summaryRef.set( Mono.from(res1.summary()).block() ); // maybe I shall make this summary a no publisher
        } ).block();

        // Then
        ResultSummary summary = summaryRef.get();
        assertNotNull( summary );
        assertThat( summary, notNullValue() );
        assertThat( summary.counters().nodesCreated(), equalTo( 0 ) );
    }

    @Test
    void shouldDiscardRecords()
    {
        // Given
        RxSession session = neo4j.driver().rxSession();
        RxResult result = session.run("UNWIND [1,2] AS a RETURN a");

        // When
        ResultSummary summary = Mono.from( result.records() ).doOnSubscribe( Subscription::cancel ).then( Mono.from( result.summary() ) ).block();

        // Then
        assertThat( summary, notNullValue() );
        assertThat( summary.counters().nodesCreated(), equalTo( 0 ) );
    }

//    @Test
//    void shouldConvertEmptyRxResultToStream()
//    {
//        RxSession session = neo4j.driver().rxSession();
//        long count = session.run( "MATCH (n:WrongLabel) RETURN n" )
//                .stream()
//                .count();
//
//        assertEquals( 0, count );
//
//        Optional<Record> anyRecord = session.run( "MATCH (n:OtherWrongLabel) RETURN n" )
//                .stream()
//                .findAny();
//
//        assertFalse( anyRecord.isPresent() );
//    }
//
//    @Test
//    void shouldConvertRxResultToStream()
//    {
//        RxSession session = neo4j.driver().rxSession();
//        List<Integer> receivedList = session.run( "UNWIND range(1, 10) AS x RETURN x" )
//                .stream()
//                .map( record -> record.get( 0 ) )
//                .map( Value::asInt )
//                .collect( toList() );
//
//        assertEquals( asList( 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ), receivedList );
//    }
//
//    @Test
//    void shouldConvertImmediatelyFailingRxResultToStream()
//    {
//        RxSession session = neo4j.driver().rxSession();
//        List<Integer> seen = new ArrayList<>();
//
//        ClientException e = assertThrows( ClientException.class,
//                () -> session.run( "RETURN 10 / 0" )
//                        .stream()
//                        .forEach( record -> seen.add( record.get( 0 ).asInt() ) ) );
//
//        assertThat( e.getMessage(), containsString( "/ by zero" ) );
//
//        assertEquals( emptyList(), seen );
//    }
//
//    @Test
//    void shouldConvertEventuallyFailingRxResultToStream()
//    {
//        RxSession session = neo4j.driver().rxSession();
//        List<Integer> seen = new ArrayList<>();
//
//        ClientException e = assertThrows( ClientException.class,
//                () -> session.run( "UNWIND range(5, 0, -1) AS x RETURN x / x" )
//                        .stream()
//                        .forEach( record -> seen.add( record.get( 0 ).asInt() ) ) );
//
//        assertThat( e.getMessage(), containsString( "/ by zero" ) );
//
//        // stream should manage to consume all elements except the last one, which produces an error
//        assertEquals( asList( 1, 1, 1, 1, 1 ), seen );
//    }
//
//    @Test
//    void shouldEmptyResultWhenConvertedToStream()
//    {
//        RxSession session = neo4j.driver().rxSession();
//        RxResult result = session.run( "UNWIND range(1, 10) AS x RETURN x" );
//
//        assertTrue( result.hasNext() );
//        assertEquals( 1, result.next().get( 0 ).asInt() );
//
//        assertTrue( result.hasNext() );
//        assertEquals( 2, result.next().get( 0 ).asInt() );
//
//        List<Integer> list = result.stream()
//                .map( record -> record.get( 0 ).asInt() )
//                .collect( toList() );
//        assertEquals( asList( 3, 4, 5, 6, 7, 8, 9, 10 ), list );
//
//        assertFalse( result.hasNext() );
//        assertThrows( NoSuchRecordException.class, result::next );
//        assertEquals( emptyList(), result.list() );
//        assertEquals( 0, result.stream().count() );
//    }
//
//    @Test
//    void shouldConsumeLargeResultAsParallelStream()
//    {
//        RxSession session = neo4j.driver().rxSession();
//        List<String> receivedList = session.run( "UNWIND range(1, 200000) AS x RETURN 'value-' + x" )
//                .stream()
//                .parallel()
//                .map( record -> record.get( 0 ) )
//                .map( Value::asString )
//                .collect( toList() );
//
//        List<String> expectedList = IntStream.range( 1, 200001 )
//                .mapToObj( i -> "value-" + i )
//                .collect( toList() );
//
//        assertEquals( expectedList, receivedList );
//    }
}
