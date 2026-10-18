package br.com.matheuslealpa.queueflow.shared.infrastructure;

import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Executa cada caso de uso em uma transação (READ COMMITTED, padrão do PostgreSQL). Exceções
 * não verificadas desfazem a transação.
 */
@Component
public class SpringUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactions;

    public SpringUnitOfWork(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
